package com.example.backend.medication;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "medications")
@Getter
@NoArgsConstructor
public class Medication {
    @Id @Column(length = 13) private String code;
    @Column(nullable = false, columnDefinition = "TEXT") private String name;
    @Column(nullable = false, length = 300) private String manufacturer;
    @Column(nullable = false, length = 100) private String specification;
    @Column(nullable = false, length = 30) private String category;
    @Column(nullable = false, length = 20) private String productCode;
    @Column(nullable = false, length = 20) private String ingredientCode;
}
