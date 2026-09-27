package com.example.backend.order;

import com.example.backend.encounter.entity.Encounter;
import com.example.backend.encounter.repository.EncounterRepository;
import com.example.backend.member.entity.*;
import com.example.backend.member.repository.MemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
@Validated
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN')")
public class ClinicalOrderService {
    private final ClinicalOrderRepository orders;
    private final OrderItemRepository items;
    private final EncounterRepository encounters;
    private final MemberRepository members;
    public record Create(@NotNull @Positive Long encounterId, @Positive Long itemId,
                         @NotNull @Size(max = 2000) String instructions,
                         @Size(max = 200) String itemName, OrderItem.Type type) {
        public Create(Long encounterId, Long itemId, String instructions) {
            this(encounterId, itemId, instructions, null, null);
        }
    }
    public record Change(@PositiveOrZero long version, @NotNull @Size(max = 20000) String text) {}
    public record ItemView(Long id, String code, String name, OrderItem.Type type) {}
    public record OrderPage(List<OrderView> items, int page, int totalPages, long totalElements) {}

    @Transactional(readOnly = true)
    public List<ItemView> items(Authentication auth) {
        actor(auth);
        return items.findByActiveTrueOrderByTypeAscNameAsc().stream().map(i -> new ItemView(i.getId(),i.getCode(),i.getName(),i.getType())).toList();
    }
    @Transactional(readOnly = true)
    public OrderPage list(LocalDate date, Long encounterId, Long patientId, OrderItem.Type type,
                          String status, boolean mine, boolean unreviewed, int page, Authentication auth) {
        var actor = actor(auth);
        if (page < 0 || page > 100000) throw new IllegalArgumentException("페이지 번호가 올바르지 않습니다.");
        var statuses = switch (status) {
            case "ALL" -> List.of(ClinicalOrder.Status.values());
            case "ACTIVE" -> List.of(ClinicalOrder.Status.REQUESTED, ClinicalOrder.Status.IN_PROGRESS);
            case "REQUESTED" -> List.of(ClinicalOrder.Status.REQUESTED);
            case "IN_PROGRESS" -> List.of(ClinicalOrder.Status.IN_PROGRESS);
            case "COMPLETED" -> List.of(ClinicalOrder.Status.COMPLETED);
            case "CANCELLED" -> List.of(ClinicalOrder.Status.CANCELLED);
            default -> throw new IllegalArgumentException("요청 상태가 올바르지 않습니다.");
        };
        var from = (date == null ? LocalDate.of(1900,1,1) : date).atStartOfDay();
        var to = (date == null ? LocalDate.of(9999,12,31) : date.plusDays(1)).atStartOfDay();
        var result = orders.search(from,to,encounterId,patientId,type,statuses,mine ? actor.getId() : null,unreviewed,PageRequest.of(page,50));
        return new OrderPage(result.map(OrderView::from).getContent(),page,result.getTotalPages(),result.getTotalElements());
    }
    @PreAuthorize("hasRole('DOCTOR')")
    public OrderView create(@Valid Create input, Authentication auth) {
        var actor = actor(auth);
        requireRole(actor, Role.DOCTOR);
        var encounter = encounters.findLockedById(input.encounterId()).orElseThrow(() -> missing("진료"));
        if (encounter.getStatus() != Encounter.Status.IN_PROGRESS || encounter.getDoctor() == null
                || !encounter.getDoctor().getId().equals(actor.getId()))
            throw new IllegalStateException("담당 의사가 진행 중인 진료에서만 요청할 수 있습니다.");
        OrderItem item;
        if (input.itemId() != null) {
            item = items.findById(input.itemId()).filter(OrderItem::isActive).orElseThrow(() -> missing("사용 가능한 검사·처치 항목"));
        } else {
            if (input.type() == null || input.itemName() == null || input.itemName().isBlank())
                throw new IllegalArgumentException("검사·처치 분류와 항목명을 입력하세요.");
            String name = input.itemName().strip();
            item = items.findFirstByTypeAndNameAndActiveTrueOrderByIdAsc(input.type(), name)
                .orElseGet(() -> items.save(new OrderItem("LOCAL-" + UUID.randomUUID().toString().replace("-", ""), name, input.type())));
        }
        return OrderView.from(orders.saveAndFlush(new ClinicalOrder(encounter,item,actor,input.instructions())));
    }
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public OrderView start(Long id, @Valid Change input, Authentication auth) {
        var actor = actor(auth); requireRole(actor, Role.DOCTOR, Role.NURSE);
        var order = locked(id,input.version()); order.start(actor); return saved(order);
    }
    @PreAuthorize("hasAnyRole('DOCTOR','NURSE')")
    public OrderView complete(Long id, @Valid Change input, Authentication auth) {
        var actor = actor(auth); requireRole(actor, Role.DOCTOR, Role.NURSE);
        var order = locked(id,input.version()); order.complete(actor,input.text()); return saved(order);
    }
    @PreAuthorize("hasRole('DOCTOR')")
    public OrderView cancel(Long id, @Valid Change input, Authentication auth) {
        var actor = actor(auth); requireRole(actor, Role.DOCTOR);
        var order = locked(id,input.version()); order.cancel(actor,input.text()); return saved(order);
    }
    @PreAuthorize("hasRole('DOCTOR')")
    public OrderView review(Long id, @Valid Change input, Authentication auth) {
        var actor = actor(auth); requireRole(actor, Role.DOCTOR);
        var order = locked(id,input.version()); order.review(actor); return saved(order);
    }
    private OrderView saved(ClinicalOrder order) { orders.flush(); return OrderView.from(order); }
    private ClinicalOrder locked(Long id, long version) {
        var order = orders.findLockedById(id).orElseThrow(() -> missing("요청")); order.requireVersion(version); return order;
    }
    private Member actor(Authentication auth) {
        return members.findByEmail(auth.getName()).filter(Member::isActive)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"활성 계정이 필요합니다."));
    }
    private ResponseStatusException missing(String name) { return new ResponseStatusException(HttpStatus.NOT_FOUND,name+"을 찾을 수 없습니다."); }
    private void requireRole(Member actor, Role... roles) {
        if (!Arrays.asList(roles).contains(actor.getRole()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"이 작업을 수행할 권한이 없습니다.");
    }
}
