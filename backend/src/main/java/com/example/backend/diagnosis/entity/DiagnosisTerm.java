package com.example.backend.diagnosis.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** All source names, including alternative names for the same code. */
@Entity
@Table(name = "diagnosis_terms", uniqueConstraints = @UniqueConstraint(columnNames = {"diagnosis_code", "source_row"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiagnosisTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "diagnosis_code", nullable = false)
    private DiagnosisCode diagnosis;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String koreanName;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String englishName;
    @Column(name = "source_row", nullable = false)
    private int sourceRow;
}
