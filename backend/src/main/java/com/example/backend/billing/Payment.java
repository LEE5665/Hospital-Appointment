package com.example.backend.billing;

import com.example.backend.encounter.entity.Encounter;
import com.example.backend.member.entity.Member;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
public class Payment {
    public enum Method { CASH, CARD }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "encounter_id", nullable = false, unique = true) private Encounter encounter;
    @Column(nullable = false) private long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private Method method;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Member receivedBy;
    @Column(nullable = false) private LocalDateTime paidAt;

    public Payment(Encounter encounter, long amount, Method method, Member receivedBy) {
        this.encounter = encounter; this.amount = amount; this.method = method;
        this.receivedBy = receivedBy; this.paidAt = LocalDateTime.now();
    }
}
