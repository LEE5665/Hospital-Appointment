package com.example.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Collection;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByEncounterId(Long encounterId);
    @EntityGraph(attributePaths = {"encounter", "receivedBy"})
    List<Payment> findByEncounterIdIn(Collection<Long> encounterIds);
}
