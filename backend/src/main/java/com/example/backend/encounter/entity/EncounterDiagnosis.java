package com.example.backend.encounter.entity;

import com.example.backend.diagnosis.entity.DiagnosisCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncounterDiagnosis {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnosis_code", nullable = false)
    private DiagnosisCode diagnosis;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String name;
    @Column(nullable = false, length = 30)
    private String classificationVersion;
    @Column(nullable = false)
    private boolean principal;
    @Column(nullable = false)
    private boolean principalDiagnosisAllowed;

    public EncounterDiagnosis(DiagnosisCode diagnosis, boolean principal) {
        this.diagnosis = diagnosis;
        this.name = diagnosis.getKoreanName();
        this.classificationVersion = diagnosis.getClassificationVersion();
        this.principal = principal;
        this.principalDiagnosisAllowed = diagnosis.isPrincipalDiagnosisAllowed();
    }
}
