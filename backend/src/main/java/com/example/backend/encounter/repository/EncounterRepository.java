package com.example.backend.encounter.repository;

import com.example.backend.encounter.entity.Encounter;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface EncounterRepository extends JpaRepository<Encounter, Long> {
    @EntityGraph(attributePaths = {"patient", "doctor"})
    @Query("""
        select e from Encounter e
        where e.registeredAt >= :from and e.registeredAt < :to
          and e.status in :statuses
          and (:doctorId is null or e.doctor.id = :doctorId or e.doctor is null)
        order by e.registeredAt asc, e.id asc
        """)
    org.springframework.data.domain.Page<Encounter> findQueue(
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
        @Param("statuses") Collection<Encounter.Status> statuses, @Param("doctorId") Long doctorId,
        org.springframework.data.domain.Pageable pageable);
    @EntityGraph(attributePaths = {"patient", "doctor"})
    @Query("""
        select e from Encounter e
        where e.registeredAt >= :from and e.registeredAt < :to
        order by e.registeredAt asc
        """)
    List<Encounter> findByRegisteredAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    boolean existsByPatientIdAndStatusIn(Long patientId, Collection<Encounter.Status> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Encounter e where e.id = :id")
    Optional<Encounter> findLockedById(@Param("id") Long id);
}
