package com.example.backend.diagnosis.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Code-level metadata from the first occurrence in the HIRA master. */
@Entity
@Table(name = "diagnosis_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiagnosisCode {
    @Id
    @Column(length = 20)
    private String code;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String koreanName;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String englishName;
    @Column(nullable = false)
    private boolean completeCode;
    @Column(nullable = false)
    private boolean principalDiagnosisAllowed;
    private String infectiousDiseaseClass;
    private String sexRestriction;
    private Integer minimumAge;
    private Integer maximumAge;
    private String medicineType;
    @Column(nullable = false, length = 30)
    private String classificationVersion;
    @Column(nullable = false)
    private String sourceFile;
}
