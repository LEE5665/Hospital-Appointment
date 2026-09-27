package com.example.backend.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;

public interface ClinicalOrderRepository extends JpaRepository<ClinicalOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ClinicalOrder o where o.id = :id")
    Optional<ClinicalOrder> findLockedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"encounter", "encounter.patient", "requestedBy", "performedBy", "reviewedBy", "item"})
    @Query("""
        select o from ClinicalOrder o where o.requestedAt >= :from and o.requestedAt < :to
        and (:encounterId is null or o.encounter.id = :encounterId)
        and (:patientId is null or o.encounter.patient.id = :patientId)
        and (:type is null or o.type = :type) and o.status in :statuses
        and (:requesterId is null or o.requestedBy.id = :requesterId)
        and (:unreviewed = false or (o.completedAt is not null and o.reviewedAt is null))
        order by o.requestedAt desc, o.id desc
        """)
    Page<ClinicalOrder> search(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
        @Param("encounterId") Long encounterId, @Param("patientId") Long patientId,
        @Param("type") OrderItem.Type type, @Param("statuses") Collection<ClinicalOrder.Status> statuses,
        @Param("requesterId") Long requesterId, @Param("unreviewed") boolean unreviewed, Pageable pageable);
}
