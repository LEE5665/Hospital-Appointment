package com.example.backend.encounter;

import com.example.backend.encounter.dto.*;
import com.example.backend.encounter.entity.Encounter;
import com.example.backend.encounter.repository.EncounterRepository;
import com.example.backend.encounter.service.ClinicService;
import com.example.backend.medication.MedicationController;
import com.example.backend.member.entity.*;
import com.example.backend.member.repository.MemberRepository;
import com.example.backend.patient.entity.*;
import com.example.backend.patient.repository.PatientRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@WithMockUser(roles = "DOCTOR")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:prescriptions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
class PrescriptionIntegrationTests {
    @Autowired ClinicService clinic;
    @Autowired EncounterRepository encounters;
    @Autowired MemberRepository members;
    @Autowired PatientRepository patients;
    @Autowired JdbcTemplate jdbc;
    @Autowired MedicationController search;
    private Authentication actor;
    private long id;
    private String code;
    @BeforeEach void setup() {
        var doctor = members.save(Member.builder().email(UUID.randomUUID()+"@test.local").password("unused")
            .name("의사").role(Role.DOCTOR).active(true).build());
        actor = new UsernamePasswordAuthenticationToken(doctor.getEmail(), "unused");
        var patient = patients.save(Patient.builder().chartNumber(UUID.randomUUID().toString().substring(0,20))
            .name("환자").birthDate(LocalDate.of(2000,1,1)).gender(Gender.MALE).phone("01000000000").build());
        id = encounters.saveAndFlush(new Encounter(patient, doctor, "방문 사유")).getId();
        code = String.format("880%010d", id);
        jdbc.update("insert into medications(code,name,manufacturer,specification,category,product_code,ingredient_code) values (?,?,?,?,?,?,?)",
            code, "시험약"+id, "제약사", "10mg", "전문의약품", "P"+id, "I");
    }
    private PrescriptionInput.Item item(String selectedCode, String dose) {
        return new PrescriptionInput.Item(selectedCode, new BigDecimal(dose), "정", 3, 5, "경구 · 식후");
    }
    @Test void savesSeparatelyPreservesSoapVersionsAndHistoricalNames() {
        var started = clinic.start(id, actor);
        var soap = clinic.save(id, new SoapInput("S", "", "", "P", false, started.version()), actor);
        var saved = clinic.savePrescriptions(id, new PrescriptionInput(soap.version(), List.of(item(code,"0.5"))), actor);
        assertThat(saved.version()).isGreaterThan(soap.version());
        assertThat(saved.subjective()).isEqualTo("S");
        assertThat(saved.plan()).isEqualTo("P");
        assertThat(saved.prescriptions()).hasSize(1);
        assertThat(saved.prescriptions().getFirst().dose()).isEqualByComparingTo("0.5");
        assertThatThrownBy(() -> clinic.savePrescriptions(id, new PrescriptionInput(soap.version(), List.of()), actor))
            .isInstanceOf(IllegalStateException.class);
        jdbc.update("update medications set name='새 약품명' where code=?", code);
        assertThat(clinic.encounter(id).prescriptions().getFirst().name()).isEqualTo("시험약"+id);
        var done = clinic.save(id, new SoapInput("S", "", "", "P", true, saved.version()), actor);
        assertThat(done.prescriptions()).hasSize(1);
        assertThatThrownBy(() -> clinic.savePrescriptions(id, new PrescriptionInput(done.version(), List.of()), actor))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsInvalidInputUnknownDrugsAndUnauthorizedDoctorAtomically() {
        assertThatThrownBy(() -> clinic.savePrescriptions(id, new PrescriptionInput(0,List.of(item(code,"1"))), actor))
            .isInstanceOf(IllegalStateException.class);
        var started = clinic.start(id, actor);
        for (var items : List.of(List.of(item(code,"0")), List.of(item(code,"0.0001")),
                List.of(item(code,"1"),item("missing","1"))))
            assertThatThrownBy(() -> clinic.savePrescriptions(id, new PrescriptionInput(started.version(),items), actor))
                .isInstanceOf(IllegalArgumentException.class);
        var other = members.save(Member.builder().email(UUID.randomUUID()+"@test.local").password("unused")
            .name("다른 의사").role(Role.DOCTOR).active(true).build());
        assertThatThrownBy(() -> clinic.savePrescriptions(id, new PrescriptionInput(started.version(),List.of(item(code,"1"))),
            new UsernamePasswordAuthenticationToken(other.getEmail(), "unused"))).isInstanceOf(IllegalStateException.class);
        assertThat(clinic.encounter(id).prescriptions()).isEmpty();
        assertThat(clinic.encounter(id).version()).isEqualTo(started.version());
    }
    @Test void searchesAndSupportsReplacementAndRemoval() {
        assertThat(search.search(code)).extracting(MedicationController.Result::code).containsExactly(code);
        assertThat(search.search("P"+id)).extracting(MedicationController.Result::code).contains(code);
        assertThat(search.search("%")).isEmpty();
        var started = clinic.start(id, actor);
        var saved = clinic.savePrescriptions(id,new PrescriptionInput(started.version(),List.of(item(code,"1"))),actor);
        var changed = clinic.savePrescriptions(id,new PrescriptionInput(saved.version(),List.of(item(code,"2"))),actor);
        assertThat(changed.prescriptions().getFirst().dose()).isEqualByComparingTo("2");
        var cleared = clinic.savePrescriptions(id,new PrescriptionInput(changed.version(),List.of()),actor);
        assertThat(clinic.encounter(id).prescriptions()).isEmpty();
        assertThat(cleared.version()).isGreaterThan(changed.version());
    }
    @Test @WithMockUser(roles="NURSE") void nurseCannotPrescribe() {
        assertThatThrownBy(() -> clinic.savePrescriptions(id,new PrescriptionInput(0,List.of(item(code,"1"))),actor))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
