package com.example.backend.order;

import com.example.backend.encounter.entity.Encounter;
import com.example.backend.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "clinical_orders", indexes = {
    @Index(name = "idx_order_requested", columnList = "requested_at"),
    @Index(name = "idx_order_encounter", columnList = "encounter_id"),
    @Index(name = "idx_order_status", columnList = "status") })
@Getter
@NoArgsConstructor
public class ClinicalOrder {
    public enum Status { REQUESTED, IN_PROGRESS, COMPLETED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "encounter_id") private Encounter encounter;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private OrderItem item;
    @Column(nullable = false, length = 200) private String itemName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OrderItem.Type type;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Member requestedBy;
    @Column(nullable = false, length = 2000) private String instructions;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.REQUESTED;
    @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now();
    @ManyToOne(fetch = FetchType.LAZY) private Member performedBy;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Column(nullable = false, columnDefinition = "TEXT") private String result = "";
    @Column(length = 1000) private String cancellationReason;
    private LocalDateTime cancelledAt;
    @ManyToOne(fetch = FetchType.LAZY) private Member reviewedBy;
    private LocalDateTime reviewedAt;
    @Version private long version;

    public ClinicalOrder(Encounter encounter, OrderItem item, Member requestedBy, String instructions) {
        this.encounter = encounter; this.item = item; this.itemName = item.getName(); this.type = item.getType();
        this.requestedBy = requestedBy; this.instructions = instructions.strip();
    }
    public void requireVersion(long expected) {
        if (version != expected) throw new IllegalStateException("요청 상태가 변경되었습니다. 목록을 새로고침해 주세요.");
    }
    public void start(Member actor) {
        if (status != Status.REQUESTED) throw new IllegalStateException("요청 상태에서만 수행을 시작할 수 있습니다.");
        performedBy = actor; startedAt = LocalDateTime.now(); status = Status.IN_PROGRESS;
    }
    public void complete(Member actor, String result) {
        if (status != Status.IN_PROGRESS || performedBy == null || !performedBy.getId().equals(actor.getId()))
            throw new IllegalStateException("수행을 시작한 담당자만 완료할 수 있습니다.");
        if (result == null || result.isBlank() || result.length() > 20000)
            throw new IllegalArgumentException("결과 또는 수행 내용을 1~20,000자로 입력하세요.");
        this.result = result.strip(); completedAt = LocalDateTime.now(); status = Status.COMPLETED;
    }
    public void cancel(Member actor, String reason) {
        if (status != Status.REQUESTED || !requestedBy.getId().equals(actor.getId()))
            throw new IllegalStateException("요청한 의사만 수행 전 요청을 취소할 수 있습니다.");
        if (reason == null || reason.isBlank() || reason.length() > 1000)
            throw new IllegalArgumentException("취소 사유를 1~1,000자로 입력하세요.");
        cancellationReason = reason.strip(); cancelledAt = LocalDateTime.now(); status = Status.CANCELLED;
    }
    public void review(Member actor) {
        if (status != Status.COMPLETED || !requestedBy.getId().equals(actor.getId()))
            throw new IllegalStateException("요청한 의사만 완료 결과를 확인 처리할 수 있습니다.");
        if (reviewedAt != null) throw new IllegalStateException("이미 확인한 결과입니다.");
        reviewedBy = actor; reviewedAt = LocalDateTime.now();
    }
}
