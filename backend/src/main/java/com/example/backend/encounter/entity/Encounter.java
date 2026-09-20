package com.example.backend.encounter.entity;

import com.example.backend.member.entity.Member;
import com.example.backend.patient.entity.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "encounters", indexes = @Index(name = "idx_encounter_registered", columnList = "registeredAt"))
@Getter
@NoArgsConstructor
public class Encounter {
    public enum Status { WAITING, IN_PROGRESS, COMPLETED, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY)
    private Member doctor;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status = Status.WAITING;
    @Column(nullable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    @Column(length = 500)
    private String reason;
    @Column(columnDefinition = "TEXT")
    private String note = "";
    @Version
    private long version;

    public Encounter(Patient patient, Member doctor, String reason) {
        this.patient = patient;
        this.doctor = doctor;
        this.reason = reason;
    }

    public void start(Member actor) {
        if (status != Status.WAITING) throw new IllegalStateException("대기 중인 접수만 진료를 시작할 수 있습니다.");
        if (doctor != null && !doctor.getId().equals(actor.getId()))
            throw new IllegalStateException("다른 의사에게 배정된 환자입니다.");
        doctor = actor;
        status = Status.IN_PROGRESS;
        startedAt = LocalDateTime.now();
    }

    public void saveNote(Member actor, String content, boolean complete) {
        if (status != Status.IN_PROGRESS || doctor == null || !doctor.getId().equals(actor.getId()))
            throw new IllegalStateException("담당 의사가 진행 중인 진료만 작성할 수 있습니다.");
        if (complete && content.isBlank()) throw new IllegalArgumentException("진료기록을 작성한 후 완료해 주세요.");
        note = content;
        if (complete) {
            status = Status.COMPLETED;
            completedAt = LocalDateTime.now();
        }
    }
}
