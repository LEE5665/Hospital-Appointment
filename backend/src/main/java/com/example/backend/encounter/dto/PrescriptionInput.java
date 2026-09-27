package com.example.backend.encounter.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record PrescriptionInput(@PositiveOrZero long version,
    @NotNull @Size(max = 50) List<@NotNull @Valid Item> prescriptions) {
    public record Item(@NotBlank String medicationCode,
        @NotNull @DecimalMin("0.001") @DecimalMax("99999") @Digits(integer = 5, fraction = 3) BigDecimal dose,
        @NotBlank @Size(max = 20) String unit,
        @Min(1) @Max(24) int frequency,
        @Min(1) @Max(365) int days,
        @NotBlank @Size(max = 200) String instructions) {}
}
