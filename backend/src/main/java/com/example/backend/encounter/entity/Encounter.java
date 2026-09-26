package com.example.backend.encounter.entity;

import com.example.backend.member.entity.Member;
import com.example.backend.patient.entity.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private String subjective = "";
    @Column(columnDefinition = "TEXT")
    private String objective = "";
    @Column(columnDefinition = "TEXT")
    private String assessment = "";
    @Column(columnDefinition = "TEXT")
    private String plan = "";
    @ElementCollection
    @CollectionTable(name = "encounter_diagnoses", joinColumns = @JoinColumn(name = "encounter_id"))
    @OrderColumn(name = "position")
    private List<EncounterDiagnosis> diagnoses = new ArrayList<>();
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

    public void saveSoap(Member actor, String subjective, String objective, String assessment, String plan, boolean complete) {
        if (status != Status.IN_PROGRESS || doctor == null || !doctor.getId().equals(actor.getId()))
            throw new IllegalStateException("담당 의사가 진행 중인 진료만 작성할 수 있습니다.");
        for (String section : new String[] { subjective, objective, assessment, plan }) {
            if (section == null || section.length() > 20000)
                throw new IllegalArgumentException("SOAP 각 항목은 20,000자 이내로 입력해 주세요.");
        }
        if (complete && subjective.isBlank() && objective.isBlank() && assessment.isBlank() && plan.isBlank())
            throw new IllegalArgumentException("SOAP 진료기록을 작성한 후 완료해 주세요.");
        this.subjective = subjective;
        this.objective = objective;
        this.assessment = assessment;
        this.plan = plan;
        if (complete) {
            status = Status.COMPLETED;
            completedAt = LocalDateTime.now();
        }
    }

    public void replaceDiagnoses(List<EncounterDiagnosis> values) {
        diagnoses.clear();
        diagnoses.addAll(values);
    }
}
