package com.example.backend.global.init;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.nio.charset.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:medicationImport;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
class MedicationImportTests {
    @Autowired MedicationCsvReader reader;
    @Autowired MedicationMasterImporter importer;
    @Autowired JdbcTemplate jdbc;
    private static final Charset CHARSET = Charset.forName("MS949");
    private static final String HEADER = "대표코드,표준코드,한글상품명,업체명,약품규격,전문일반구분,제품코드(개정후),일반명코드(성분명코드),취소일자\r\n";
    @BeforeEach void clear() { jdbc.update("delete from medications"); }
    private Resource csv(String body) { return new ByteArrayResource((HEADER+body).getBytes(CHARSET)); }
    @Test void handlesQuotedFieldsAndPrefersRepresentativeOverPackaging() {
        var resource = csv("8800000000001,8800000000002,포장약,업체,100정,전문의약품,P,I,\r\n"
            + "8800000000001,8800000000001,\"약, \"\"특수\"\"\n이름\",업체,없음,전문의약품,P,I,\r\n"
            + "8800000000003,8800000000003,취소약,업체,,일반의약품,,,2020-01-01\r\n"
            + "8800000000004,8800000000004,원료,업체,,원료의약품,,,\r\n");
        var rows = reader.read(resource,CHARSET);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().name()).isEqualTo("약, \"특수\"\n이름");
        assertThat(rows.getFirst().specification()).isEmpty();
        assertThat(importer.initialize(resource,CHARSET)).isEqualTo(1);
        assertThat(importer.initialize(new ClassPathResource("not-found.csv"),CHARSET)).isZero();
    }
    @Test void malformedFileDoesNotPartiallyImport() {
        assertThatThrownBy(() -> importer.initialize(csv("8800000000001,8800000000001,약,업체,,일반의약품,,,\n\"broken"),CHARSET))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select count(*) from medications",Integer.class)).isZero();
    }
    @Test void supportsUtf8BomAndDoesNotUsePackageSizeAsDose() {
        var resource = new ByteArrayResource(("\uFEFF"+HEADER
            + "8800000000001,8800000000002,희귀약,업체,100mL,\"전문,희귀\",,,\n")
            .getBytes(StandardCharsets.UTF_8));
        var entries = reader.read(resource,StandardCharsets.UTF_8);
        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().specification()).isEmpty();
        assertThat(entries.getFirst().name()).isEqualTo("희귀약");
    }
    @Test void importsActualFileWithOnlySelectedColumnsAndUniqueRepresentativeCodes() {
        int count = importer.initialize(new ClassPathResource("data/의약품표준코드.csv"),CHARSET);
        assertThat(count).isGreaterThan(50000).isLessThan(70000);
        assertThat(jdbc.queryForObject("select count(distinct code) from medications",Integer.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("select count(*) from medications where category not in ('전문의약품','일반의약품','전문,희귀')",Integer.class)).isZero();
    }
}
