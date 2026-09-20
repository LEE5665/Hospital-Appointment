package com.example.backend.appointment.entity;

import com.example.backend.encounter.entity.Encounter;
import com.example.backend.member.entity.Member;
import com.example.backend.patient.entity.Patient;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "appointments", indexes = @Index(name = "idx_appointment_scheduled", columnList = "scheduled_at"))
@Getter
@NoArgsConstructor
public class Appointment {
    public enum Status { BOOKED, CHECKED_IN, CANCELLED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY)
    private Member doctor;
    @Column(nullable = false, name = "scheduled_at")
    private LocalDateTime scheduledAt;
    @Column(length = 500)
    private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status = Status.BOOKED;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(unique = true)
    private Encounter encounter;
    @Version private long version;

    public Appointment(Patient patient, Member doctor, LocalDateTime scheduledAt, String reason) {
        this.patient = patient;
        this.doctor = doctor;
        this.scheduledAt = scheduledAt;
        this.reason = reason;
    }
    public void requireBooked() {
        if (status != Status.BOOKED) throw new IllegalStateException("예약 상태에서만 처리할 수 있습니다.");
    }
    public void cancel() { requireBooked(); status = Status.CANCELLED; }
    public void reschedule(Member doctor, LocalDateTime scheduledAt, String reason) {
        requireBooked();
        this.doctor = doctor;
        this.scheduledAt = scheduledAt;
        this.reason = reason;
    }
    public void checkIn(Encounter encounter) {
        requireBooked();
        this.encounter = encounter;
        status = Status.CHECKED_IN;
    }
}
