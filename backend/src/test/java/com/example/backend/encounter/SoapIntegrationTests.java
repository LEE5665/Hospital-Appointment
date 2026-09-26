package com.example.backend.encounter;

import com.example.backend.encounter.dto.SoapInput;
import com.example.backend.encounter.entity.Encounter;
import com.example.backend.encounter.repository.EncounterRepository;
import com.example.backend.encounter.service.ClinicService;
import com.example.backend.global.init.SoapSchemaInitializer;
import com.example.backend.member.entity.Member;
import com.example.backend.member.entity.Role;
import com.example.backend.member.repository.MemberRepository;
import com.example.backend.patient.entity.Gender;
import com.example.backend.patient.entity.Patient;
import com.example.backend.patient.repository.PatientRepository;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import com.example.backend.diagnosis.DiagnosisController;

import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@WithMockUser(roles = "DOCTOR")
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:soap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
class SoapIntegrationTests {
    @Autowired ClinicService clinic;
    @Autowired EncounterRepository encounters;
    @Autowired PatientRepository patients;
    @Autowired MemberRepository members;
    @Autowired Validator validator;
    @Autowired JdbcTemplate jdbc;
    @Autowired SoapSchemaInitializer schema;
    @Autowired DiagnosisController diagnosisSearch;
    private Authentication actor;
    private Long id;

    @BeforeEach void setUp() {
        var doctor = members.save(Member.builder().email(UUID.randomUUID() + "@test.local")
            .password("unused").name("담당의").role(Role.DOCTOR).active(true).build());
        actor = new UsernamePasswordAuthenticationToken(doctor.getEmail(), "unused");
        var patient = patients.save(Patient.builder().chartNumber(UUID.randomUUID().toString().substring(0, 20))
            .name("테스트 환자").birthDate(LocalDate.of(2000, 1, 1)).gender(Gender.MALE).phone("01000000000").build());
        id = encounters.saveAndFlush(new Encounter(patient, doctor, "두통으로 방문")).getId();
    }

    @Test void savesSectionsIndependentlyAndCompletesWithoutChangingReceptionReason() {
        var started = clinic.start(id, actor);
        var draft = clinic.save(id, new SoapInput("어제부터 두통", "", "", "", false, started.version()), actor);
        assertThat(draft.subjective()).isEqualTo("어제부터 두통");
        assertThat(draft.objective()).isEmpty();
        assertThat(draft.status()).isEqualTo(Encounter.Status.IN_PROGRESS);
        assertThat(draft.version()).isGreaterThan(started.version());
        var done = clinic.save(id, new SoapInput("어제부터 두통", "진찰 소견", "평가 내용", "추적 관찰", true, draft.version()), actor);
        var stored = clinic.encounter(id);
        assertThat(stored).isEqualTo(done);
        assertThat(stored.objective()).isEqualTo("진찰 소견");
        assertThat(stored.assessment()).isEqualTo("평가 내용");
        assertThat(stored.plan()).isEqualTo("추적 관찰");
        assertThat(stored.reason()).isEqualTo("두통으로 방문");
        assertThat(stored.status()).isEqualTo(Encounter.Status.COMPLETED);
        assertThat(encounters.findById(id).orElseThrow().getCompletedAt()).isNotNull();
        assertThatThrownBy(() -> clinic.save(id, new SoapInput("수정", "", "", "", false, done.version()), actor))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsWritingBeforeStartAndCompletionWithOnlyWhitespace() {
        assertThatThrownBy(() -> clinic.save(id, new SoapInput("내용", "", "", "", false, 0), actor))
            .isInstanceOf(IllegalStateException.class);
        var started = clinic.start(id, actor);
        var empty = clinic.save(id, new SoapInput("", "", "", "", false, started.version()), actor);
        assertThatThrownBy(() -> clinic.save(id, new SoapInput(" ", "\n", "", "\t", true, empty.version()), actor))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(clinic.encounter(id).status()).isEqualTo(Encounter.Status.IN_PROGRESS);
    }

    @Test void rejectsAnotherDoctorAndStaleVersionWithoutOverwriting() {
        var started = clinic.start(id, actor);
        var other = members.save(Member.builder().email(UUID.randomUUID() + "@test.local")
            .password("unused").name("다른 의사").role(Role.DOCTOR).active(true).build());
        var otherAuth = new UsernamePasswordAuthenticationToken(other.getEmail(), "unused");
        assertThatThrownBy(() -> clinic.save(id, new SoapInput("변경", "", "", "", false, started.version()), otherAuth))
            .isInstanceOf(IllegalStateException.class);
        clinic.save(id, new SoapInput("저장한 내용", "", "", "", false, started.version()), actor);
        assertThatThrownBy(() -> clinic.save(id, new SoapInput("오래된 내용", "", "", "", false, started.version()), actor))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("기록이 변경");
        assertThat(clinic.encounter(id).subjective()).isEqualTo("저장한 내용");
    }

    @Test @WithMockUser(roles = "NURSE")
    void rejectsNurseWrites() {
        assertThatThrownBy(() -> clinic.save(id, new SoapInput("내용", "", "", "", false, 0), actor))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void validatesMissingAndOversizedFields() {
        assertThat(validator.validate(new SoapInput(null, "", "", "", false, 0))).isNotEmpty();
        assertThat(validator.validate(new SoapInput("", "", "", "x".repeat(20001), false, 0))).isNotEmpty();
        assertThat(validator.validate(new SoapInput("", "", "", "", false, 0))).isEmpty();
        assertThat(validator.validate(new SoapInput("", "", "", "", false, 0, null))).isNotEmpty();
    }

    @Test void searchesCatalogAndAliasesButExcludesIncompleteCodes() {
        seedCode("J001", "검색 테스트 감기", true, true);
        seedCode("J002", "검색 테스트 상위분류", false, true);
        jdbc.update("insert into diagnosis_terms(diagnosis_code,korean_name,english_name,source_row) values (?,?,?,?)",
            "J001", "별칭테스트", "AliasTest", 1);
        assertThat(diagnosisSearch.search("검색 테스트")).extracting(DiagnosisController.Result::code).containsExactly("J001");
        assertThat(diagnosisSearch.search("별칭테스트")).extracting(DiagnosisController.Result::code).containsExactly("J001");
        assertThat(diagnosisSearch.search("aliastest")).extracting(DiagnosisController.Result::code).containsExactly("J001");
        assertThat(diagnosisSearch.search("j00.1")).extracting(DiagnosisController.Result::code).containsExactly("J001");
        assertThat(diagnosisSearch.search("%")).isEmpty();
        assertThat(diagnosisSearch.search(" ")).isEmpty();
    }

    @Test void savesDiagnosesWithSoapAndVersionsDiagnosisOnlyChanges() {
        seedCode("T001", "주진단 테스트", true, true);
        seedCode("T002", "부진단 테스트", true, false);
        var started = clinic.start(id, actor);
        var saved = clinic.save(id, new SoapInput("S", "O", "A", "P", false, started.version(), List.of(
            new SoapInput.DiagnosisInput("T001", true), new SoapInput.DiagnosisInput("T002", false))), actor);
        var read = clinic.encounter(id);
        assertThat(read.diagnoses()).hasSize(2);
        assertThat(read.diagnoses().getFirst().name()).isEqualTo("주진단 테스트");
        assertThat(read.diagnoses().getFirst().classificationVersion()).isEqualTo("KCD-9");
        assertThat(read.diagnoses().getFirst().principal()).isTrue();
        assertThat(read.diagnoses().get(1).principalDiagnosisAllowed()).isFalse();
        var changed = clinic.save(id, new SoapInput("S", "O", "A", "P", false, saved.version(),
            List.of(new SoapInput.DiagnosisInput("T001", true))), actor);
        assertThat(changed.version()).isGreaterThan(saved.version());
        assertThat(clinic.encounter(id).diagnoses()).hasSize(1);
        assertThatThrownBy(() -> clinic.save(id, new SoapInput("S", "O", "A", "P", false, saved.version()), actor))
            .isInstanceOf(IllegalStateException.class);
        var cleared = clinic.save(id, new SoapInput("S", "O", "A", "P", false, changed.version()), actor);
        assertThat(clinic.encounter(id).diagnoses()).isEmpty();
        var completed = clinic.save(id, new SoapInput("S", "O", "A", "P", true, cleared.version(),
            List.of(new SoapInput.DiagnosisInput("T001", true))), actor);
        assertThat(completed.diagnoses()).hasSize(1);
        assertThat(clinic.encounter(id).diagnoses()).isEqualTo(completed.diagnoses());
    }

    @Test void invalidDiagnosesDoNotPartiallySaveSoap() {
        seedCode("V001", "허용", true, true);
        seedCode("V002", "불완전", false, true);
        seedCode("V003", "주진단 불가", true, false);
        var started = clinic.start(id, actor);
        for (var selection : List.of(
            List.of(new SoapInput.DiagnosisInput("MISSING", true)),
            List.of(new SoapInput.DiagnosisInput("V002", true)),
            List.of(new SoapInput.DiagnosisInput("V003", true)),
            List.of(new SoapInput.DiagnosisInput("V001", true), new SoapInput.DiagnosisInput("V001", false)))) {
            assertThatThrownBy(() -> clinic.save(id, new SoapInput("must not save", "", "", "", false, started.version(), selection), actor))
                .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> clinic.save(id, new SoapInput("S", "", "", "", true, started.version(),
            List.of(new SoapInput.DiagnosisInput("V001", false))), actor)).isInstanceOf(IllegalArgumentException.class);
        assertThat(clinic.encounter(id).subjective()).isEmpty();
        assertThat(clinic.encounter(id).diagnoses()).isEmpty();
    }

    private void seedCode(String code, String name, boolean complete, boolean principalAllowed) {
        jdbc.update("""
            insert into diagnosis_codes(code,korean_name,english_name,complete_code,principal_diagnosis_allowed,classification_version,source_file)
            values (?,?,?,?,?,'KCD-9','test')
            """, code, name, "Test diagnosis", complete, principalAllowed);
    }

    @Test void discardsOnlyLegacyColumnAndMigrationCanRunAgain() {
        var started = clinic.start(id, actor);
        clinic.save(id, new SoapInput("새 SOAP", "", "", "", false, started.version()), actor);
        jdbc.execute("alter table encounters add column note text");
        jdbc.update("update encounters set note = 'old text' where id = ?", id);
        schema.run();
        schema.run();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='ENCOUNTERS' and column_name='NOTE'", Integer.class)).isZero();
        assertThat(clinic.encounter(id).subjective()).isEqualTo("새 SOAP");
        assertThat(clinic.encounter(id).reason()).isEqualTo("두통으로 방문");
    }
}
