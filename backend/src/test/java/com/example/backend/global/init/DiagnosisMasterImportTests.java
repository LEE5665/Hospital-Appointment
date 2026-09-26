package com.example.backend.global.init;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:diagnosis;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
class DiagnosisMasterImportTests {
    @Autowired DiagnosisMasterImporter importer;
    @Autowired DiagnosisMasterReader reader;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void clear() {
        jdbc.update("delete from diagnosis_terms");
        jdbc.update("delete from diagnosis_codes");
    }

    @Test void importsRealMasterAndSkipsSecondRunEvenWhenFileIsUnavailable() {
        var result = importer.initialize(new ClassPathResource("data/배포용 상병마스터.xlsx"), "KCD-9");
        assertThat(result.codes()).isEqualTo(41660);
        assertThat(result.terms()).isEqualTo(72822);
        assertThat(jdbc.queryForObject("select count(*) from diagnosis_codes", Integer.class)).isEqualTo(41660);
        assertThat(jdbc.queryForObject("select count(*) from diagnosis_terms", Integer.class)).isEqualTo(72822);
        assertThat(jdbc.queryForObject("select count(*) from diagnosis_codes where complete_code=false or code='A00'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select korean_name from diagnosis_codes where code='A000'", String.class))
                .isEqualTo("비브리오 콜레라 01 콜레라형균에 의한 콜레라");
        assertThat(jdbc.queryForList("select korean_name from diagnosis_terms where diagnosis_code='A000'", String.class))
                .contains("고전적 콜레라");
        assertThat(importer.initialize(new ClassPathResource("missing.xlsx"), "KCD-9").skipped()).isTrue();
    }

    @Test void rollsBackCodesWhenTermInsertionFailsAndAllowsRetry() throws Exception {
        jdbc.execute("alter table diagnosis_terms add constraint reject_test_term check (korean_name <> 'fail')");
        try {
            assertThatThrownBy(() -> importer.initialize(workbook("fail"), "KCD-9")).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select count(*) from diagnosis_codes", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from diagnosis_terms", Integer.class)).isZero();
        } finally {
            jdbc.execute("alter table diagnosis_terms drop constraint reject_test_term");
        }
        assertThat(importer.initialize(workbook("별칭"), "KCD-9").codes()).isEqualTo(1);
    }

    @Test void rejectsWrongSheetAndMissingName() throws Exception {
        assertThatThrownBy(() -> reader.read(workbook(""))).hasStackTraceContaining("한글명 누락");
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            book.createSheet("삭제코드");
            book.write(out);
            assertThatThrownBy(() -> reader.read(new ByteArrayResource(out.toByteArray())))
                    .hasStackTraceContaining("시트가 없습니다");
        }
    }

    private ByteArrayResource workbook(String alias) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet(DiagnosisMasterReader.SHEET);
            var header = sheet.createRow(10);
            String[] names = {"상병기호", "한글명", "영문영", "완전코드구분", "주상병사용구분",
                    "법정감염병구분", "성별구분", "상한연령", "하한연령", "양•한방구분"};
            for (int i = 0; i < names.length; i++) header.createCell(i + 1).setCellValue(names[i]);
            for (int i = 0; i < 2; i++) {
                var row = sheet.createRow(11 + i);
                row.createCell(1).setCellValue("A000");
                row.createCell(2).setCellValue(i == 0 ? "대표명" : alias);
                row.createCell(3).setCellValue("Example");
            }
            book.write(out);
            return new ByteArrayResource(out.toByteArray()) {
                @Override public String getFilename() { return "test.xlsx"; }
            };
        }
    }
}
