package com.example.backend.encounter.entity;

import com.example.backend.medication.Medication;
import com.example.backend.encounter.dto.PrescriptionInput;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor
public class EncounterPrescription {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_code", nullable = false)
    private Medication medication;
    @Column(nullable = false, columnDefinition = "TEXT") private String name;
    @Column(nullable = false, length = 300) private String manufacturer;
    @Column(nullable = false, length = 100) private String specification;
    @Column(nullable = false, precision = 8, scale = 3) private BigDecimal dose;
    @Column(nullable = false, length = 20) private String unit;
    @Column(nullable = false) private int frequency;
    @Column(nullable = false) private int days;
    @Column(nullable = false, length = 200) private String instructions;

    public EncounterPrescription(Medication medication, PrescriptionInput.Item item) {
        this.medication = medication;
        this.name = medication.getName();
        this.manufacturer = medication.getManufacturer();
        this.specification = medication.getSpecification();
        this.dose = item.dose(); this.unit = item.unit().strip();
        this.frequency = item.frequency(); this.days = item.days();
        this.instructions = item.instructions().strip();
    }
}
