package com.example.backend.appointment.repository;

import com.example.backend.appointment.entity.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    @Query("select a.patient.id from Appointment a where a.id = :id")
    Optional<Long> findPatientIdById(@Param("id") Long id);
    @EntityGraph(attributePaths = {"patient", "doctor", "encounter"})
    @Query("""
        select a from Appointment a
        where a.patient.id = :patientId and a.scheduledAt >= :from and a.scheduledAt < :to
          and a.status = com.example.backend.appointment.entity.Appointment.Status.BOOKED
        order by a.scheduledAt, a.id
        """)
    List<Appointment> findBookedForPatient(@Param("patientId") Long patientId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = {"patient", "doctor", "encounter"})
    @Query("select a from Appointment a where a.scheduledAt >= :from and a.scheduledAt < :to order by a.scheduledAt, a.id")
    List<Appointment> findForDate(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findLockedById(@Param("id") Long id);

    @Query("""
        select count(a) > 0 from Appointment a
        where a.status <> com.example.backend.appointment.entity.Appointment.Status.CANCELLED
          and a.scheduledAt = :time
          and (a.patient.id = :patientId or a.doctor.id = :doctorId)
        """)
    boolean existsConflict(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId, @Param("time") LocalDateTime time);

    @Query("""
        select count(a) > 0 from Appointment a
        where a.id <> :excludeId
          and a.status <> com.example.backend.appointment.entity.Appointment.Status.CANCELLED
          and a.scheduledAt = :time
          and (a.patient.id = :patientId or a.doctor.id = :doctorId)
        """)
    boolean existsConflictExcluding(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId,
        @Param("time") LocalDateTime time, @Param("excludeId") Long excludeId);
}
