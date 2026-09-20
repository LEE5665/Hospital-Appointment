package com.example.backend.patient.repository;

import com.example.backend.patient.entity.Patient;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    @Query("""
        select p from Patient p
        where lower(p.name) like lower(concat('%', :query, '%'))
           or p.phone like concat('%', :query, '%')
           or lower(p.chartNumber) like lower(concat('%', :query, '%'))
        order by p.id desc
        """)
    List<Patient> search(@Param("query") String query, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> findLockedById(@Param("id") Long id);

    Optional<Patient> findByChartNumber(String chartNumber);

    List<Patient> findByNameContaining(String name);

    List<Patient> findByPhoneContaining(String phone);
}
