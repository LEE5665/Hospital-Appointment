package com.example.backend.order;

import com.example.backend.encounter.dto.SoapInput;
import com.example.backend.encounter.entity.Encounter;
import com.example.backend.encounter.repository.EncounterRepository;
import com.example.backend.encounter.service.ClinicService;
import com.example.backend.global.init.OrderItemInitializer;
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
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("test")
@WithMockUser(roles={"DOCTOR","NURSE"})
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:clinicalOrders;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa",
    "spring.datasource.password=","spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false","spring.jpa.properties.hibernate.default_schema=PUBLIC"
})
class ClinicalOrderIntegrationTests {
    @Autowired ClinicalOrderService service;
    @Autowired ClinicService clinic;
    @Autowired EncounterRepository encounters;
    @Autowired ClinicalOrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired MemberRepository members;
    @Autowired PatientRepository patients;
    @Autowired JdbcTemplate jdbc;
    private Member doctor, nurse, otherDoctor, otherNurse;
    private Patient patient;
    private Encounter encounter;
    private OrderItem test, procedure;
    private Member member(Role role) {
        return members.save(Member.builder().email(UUID.randomUUID()+"@test.local").password("unused")
            .name(role.name()).role(role).active(true).build());
    }
    private Authentication auth(Member actor) { return new UsernamePasswordAuthenticationToken(actor.getEmail(),"unused"); }
    @BeforeEach void setup() {
        doctor=member(Role.DOCTOR); nurse=member(Role.NURSE); otherDoctor=member(Role.DOCTOR); otherNurse=member(Role.NURSE);
        patient=patients.save(Patient.builder().chartNumber(UUID.randomUUID().toString().substring(0,20))
            .name("검사 환자").birthDate(LocalDate.of(2000,1,1)).gender(Gender.MALE).phone("01000000000").build());
        encounter=encounters.saveAndFlush(new Encounter(patient,doctor,"방문 사유"));
        test=items.save(new OrderItem(UUID.randomUUID().toString(),"검사 항목",OrderItem.Type.TEST));
        procedure=items.save(new OrderItem(UUID.randomUUID().toString(),"처치 항목",OrderItem.Type.PROCEDURE));
    }
    private OrderView create(OrderItem item) {
        if (encounters.findById(encounter.getId()).orElseThrow().getStatus()==Encounter.Status.WAITING)
            clinic.start(encounter.getId(),auth(doctor));
        return service.create(new ClinicalOrderService.Create(encounter.getId(),item.getId(),"요청 사항"),auth(doctor));
    }
    private ClinicalOrderService.Change change(OrderView order,String text) { return new ClinicalOrderService.Change(order.version(),text); }
    private ClinicalOrderService.OrderPage list(Long encounterId,Long patientId,OrderItem.Type type,String status,boolean unreviewed) {
        return service.list(null,encounterId,patientId,type,status,false,unreviewed,0,auth(doctor));
    }
    @Test void completesAfterEncounterEndsAndDoctorReviewsWithoutChangingSoap() {
        var requested=create(test);
        var soap=clinic.encounter(encounter.getId());
        clinic.save(encounter.getId(),new SoapInput("S","O","A","P",true,soap.version()),auth(doctor));
        var started=service.start(requested.id(),change(requested,""),auth(nurse));
        assertThat(started.performedById()).isEqualTo(nurse.getId());
        assertThat(started.version()).isGreaterThan(requested.version());
        var completed=service.complete(started.id(),change(started,"검사 결과 기록"),auth(nurse));
        assertThat(completed.completedAt()).isNotNull();
        assertThat(completed.result()).isEqualTo("검사 결과 기록");
        assertThat(list(encounter.getId(),null,null,"COMPLETED",true).items()).hasSize(1);
        var reviewed=service.review(completed.id(),change(completed,""),auth(doctor));
        assertThat(reviewed.reviewedAt()).isNotNull();
        assertThat(list(encounter.getId(),null,null,"COMPLETED",true).items()).isEmpty();
        assertThat(clinic.encounter(encounter.getId()).subjective()).isEqualTo("S");
        assertThat(clinic.encounter(encounter.getId()).status()).isEqualTo(Encounter.Status.COMPLETED);
        assertThatThrownBy(() -> service.complete(reviewed.id(),change(reviewed,"덮어쓰기"),auth(nurse)))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void registersTypedNamesReusesMatchingItemsAndValidatesRequiredFields() {
        clinic.start(encounter.getId(),auth(doctor));
        String name = "직접 입력 " + UUID.randomUUID();
        var input = new ClinicalOrderService.Create(encounter.getId(),null,"요청 사항",name,OrderItem.Type.TEST);
        var first = service.create(input,auth(doctor));
        var second = service.create(input,auth(doctor));
        assertThat(first.itemName()).isEqualTo(name);
        assertThat(first.type()).isEqualTo(OrderItem.Type.TEST);
        assertThat(second.itemCode()).isEqualTo(first.itemCode());
        var procedureOrder = service.create(new ClinicalOrderService.Create(encounter.getId(),null,"",name,OrderItem.Type.PROCEDURE),auth(doctor));
        assertThat(procedureOrder.itemCode()).isNotEqualTo(first.itemCode());
        assertThatThrownBy(() -> service.create(new ClinicalOrderService.Create(encounter.getId(),null,"","  ",OrderItem.Type.TEST),auth(doctor)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(new ClinicalOrderService.Create(encounter.getId(),null,"",name,null),auth(doctor)))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsWrongDoctorInactiveItemAndCreationOutsideActiveEncounter() {
        var input=new ClinicalOrderService.Create(encounter.getId(),test.getId(),"");
        assertThatThrownBy(() -> service.create(input,auth(doctor))).isInstanceOf(IllegalStateException.class);
        clinic.start(encounter.getId(),auth(doctor));
        assertThatThrownBy(() -> service.create(input,auth(otherDoctor))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.create(input,auth(nurse))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("update order_items set active=false where id=?",test.getId());
        assertThatThrownBy(() -> service.create(input,auth(doctor))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(list(encounter.getId(),null,null,"ALL",false).items()).isEmpty();
    }
    @Test void enforcesPerformerOwnershipStaleVersionsAndResultRequirement() {
        var requested=create(procedure);
        var started=service.start(requested.id(),change(requested,""),auth(nurse));
        assertThatThrownBy(() -> service.start(requested.id(),change(requested,""),auth(otherNurse))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.complete(started.id(),change(started,"수행"),auth(otherNurse))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.complete(started.id(),change(started," \n"),auth(nurse))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.cancel(started.id(),change(started,"취소"),auth(doctor))).isInstanceOf(IllegalStateException.class);
        var completed=service.complete(started.id(),change(started,"처치 수행 기록"),auth(nurse));
        assertThatThrownBy(() -> service.review(completed.id(),change(completed,""),auth(otherDoctor))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.review(completed.id(),change(completed,""),auth(nurse))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void cancellationKeepsReasonAndPreventsExecution() {
        var requested=create(procedure);
        assertThatThrownBy(() -> service.cancel(requested.id(),change(requested,"사유"),auth(otherDoctor))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.cancel(requested.id(),change(requested,""),auth(doctor))).isInstanceOf(IllegalArgumentException.class);
        var cancelled=service.cancel(requested.id(),change(requested,"요청 변경"),auth(doctor));
        assertThat(cancelled.cancellationReason()).isEqualTo("요청 변경");
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThatThrownBy(() -> service.start(cancelled.id(),change(cancelled,""),auth(nurse))).isInstanceOf(IllegalStateException.class);
    }
    @Test void listsPatientHistoryAcrossEncountersAndFiltersByTypeDateAndStatus() {
        var first=create(test);
        var second=encounters.saveAndFlush(new Encounter(patient,doctor,"재방문"));
        clinic.start(second.getId(),auth(doctor));
        var later=service.create(new ClinicalOrderService.Create(second.getId(),procedure.getId(),""),auth(doctor));
        jdbc.update("update clinical_orders set requested_at=? where id=?",LocalDate.now().minusDays(2).atStartOfDay(),first.id());
        assertThat(list(null,patient.getId(),null,"ALL",false).items()).extracting(OrderView::id).containsExactly(later.id(),first.id());
        assertThat(list(encounter.getId(),null,OrderItem.Type.PROCEDURE,"ALL",false).items()).isEmpty();
        assertThat(list(null,patient.getId(),OrderItem.Type.TEST,"ACTIVE",false).items()).extracting(OrderView::id).containsExactly(first.id());
        assertThat(service.list(LocalDate.now(),null,patient.getId(),null,"ALL",false,false,0,auth(doctor)).items())
            .extracting(OrderView::id).containsExactly(later.id());
        assertThat(service.list(null,null,patient.getId(),null,"ALL",true,false,0,auth(otherDoctor)).items()).isEmpty();
        jdbc.update("update order_items set name='변경된 목록명' where id=?",test.getId());
        assertThat(list(encounter.getId(),null,null,"ALL",false).items().getFirst().itemName()).isEqualTo("검사 항목");
    }
    @Test @WithMockUser(roles="NURSE") void nurseCannotCreateOrReview() {
        assertThatThrownBy(() -> service.create(new ClinicalOrderService.Create(encounter.getId(),test.getId(),""),auth(nurse)))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> service.review(1L,new ClinicalOrderService.Change(0,""),auth(nurse)))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test @WithMockUser(roles="ADMIN") void adminCannotExecute() {
        assertThatThrownBy(() -> service.start(1L,new ClinicalOrderService.Change(0,""),auth(doctor)))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
