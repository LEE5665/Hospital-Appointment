package com.example.backend.encounter.dto;

import com.example.backend.patient.entity.Patient;
import com.example.backend.patient.entity.Gender;
import java.time.LocalDate;

public record PatientView(Long id, String chartNumber, String name, LocalDate birthDate, Gender gender, String phone, String address, String allergies, String medicalHistory, String memo) {
    public static PatientView from(Patient p) { return new PatientView(p.getId(), p.getChartNumber(), p.getName(), p.getBirthDate(), p.getGender(), p.getPhone(), p.getAddress(), p.getAllergies(), p.getMedicalHistory(), p.getMemo()); }
}
