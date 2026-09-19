package com.example.backend.patient.repository;

import com.example.backend.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByChartNumber(String chartNumber);

    List<Patient> findByNameContaining(String name);

    List<Patient> findByPhoneContaining(String phone);
}
