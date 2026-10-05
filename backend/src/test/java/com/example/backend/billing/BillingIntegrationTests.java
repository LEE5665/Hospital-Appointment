package com.example.backend.billing;

import com.example.backend.encounter.entity.Encounter;
import com.example.backend.encounter.repository.EncounterRepository;
import com.example.backend.member.entity.*;
import com.example.backend.member.repository.MemberRepository;
import com.example.backend.patient.entity.*;
import com.example.backend.patient.repository.PatientRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@WithMockUser(roles="NURSE")
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:billing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false", "spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
class BillingIntegrationTests {
    @Autowired BillingService service;
    @Autowired EncounterRepository encounters;
    @Autowired PaymentRepository payments;
    @Autowired MemberRepository members;
    @Autowired PatientRepository patients;
    private Member nurse;
    private Encounter completed, waiting;
    @BeforeEach void setup() {
        var doctor = members.save(Member.builder().email(UUID.randomUUID()+"@test.local").password("unused")
            .name("의사").role(Role.DOCTOR).active(true).build());
        nurse = members.save(Member.builder().email(UUID.randomUUID()+"@test.local").password("unused")
            .name("수납 담당자").role(Role.NURSE).active(true).build());
        var patient = patients.save(Patient.builder().chartNumber(UUID.randomUUID().toString().substring(0,20))
            .name("수납 환자").birthDate(LocalDate.of(2000,1,1)).gender(Gender.MALE).phone("01000000000").build());
        completed = new Encounter(patient,doctor,"진료");
        completed.start(doctor); completed.saveSoap(doctor,"기록","","","",true);
        completed = encounters.saveAndFlush(completed);
        waiting = encounters.saveAndFlush(new Encounter(patient,doctor,"대기"));
    }
    private UsernamePasswordAuthenticationToken auth() {
        return new UsernamePasswordAuthenticationToken(nurse.getEmail(),"unused");
    }
    @Test void paysOnceAndKeepsClinicalStatus() {
        assertThat(service.list(LocalDate.now())).filteredOn(r -> r.encounterId().equals(completed.getId()))
            .singleElement().satisfies(r -> assertThat(r.paidAt()).isNull());
        var row = service.pay(completed.getId(),new BillingService.Input(15000L,Payment.Method.CARD),auth());
        assertThat(row.amount()).isEqualTo(15000L);
        assertThat(row.method()).isEqualTo(Payment.Method.CARD);
        assertThat(row.receivedByName()).isEqualTo(nurse.getName());
        assertThat(row.paidAt()).isNotNull();
        assertThat(service.list(LocalDate.now())).filteredOn(r -> r.encounterId().equals(completed.getId()))
            .singleElement().satisfies(r -> assertThat(r.amount()).isEqualTo(15000L));
        assertThatThrownBy(() -> service.pay(completed.getId(),new BillingService.Input(20000L,Payment.Method.CASH),auth()))
            .isInstanceOf(IllegalStateException.class);
        assertThat(payments.findByEncounterIdIn(java.util.List.of(completed.getId()))).hasSize(1);
        assertThat(encounters.findById(completed.getId()).orElseThrow().getStatus()).isEqualTo(Encounter.Status.COMPLETED);
    }
    @Test void rejectsUnfinishedAndInvalidPayments() {
        assertThatThrownBy(() -> service.pay(waiting.getId(),new BillingService.Input(1000L,Payment.Method.CASH),auth()))
            .isInstanceOf(IllegalStateException.class);
        for (Long amount : new Long[]{null,0L,-1L,1000000000L})
            assertThatThrownBy(() -> service.pay(completed.getId(),new BillingService.Input(amount,Payment.Method.CASH),auth()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.pay(completed.getId(),new BillingService.Input(1000L,null),auth()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.list(LocalDate.now())).noneMatch(r -> r.encounterId().equals(waiting.getId()));
    }
    @Test @WithMockUser(roles="DOCTOR") void doctorCannotCollectPayment() {
        assertThatThrownBy(() -> service.pay(completed.getId(),new BillingService.Input(1000L,Payment.Method.CASH),auth()))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
