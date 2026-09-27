package com.example.backend.medication;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface MedicationRepository extends JpaRepository<Medication, String> {
    @Query("""
        select m from Medication m where m.code like :prefix escape '!'
        or m.productCode like :prefix escape '!'
        or lower(m.name) like :term escape '!'
        order by m.name, m.code
        """)
    List<Medication> search(@Param("prefix") String prefix, @Param("term") String term, Pageable page);
}
