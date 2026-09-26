package com.example.backend.encounter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record SoapInput(
    @NotNull @Size(max = 20000) String subjective,
    @NotNull @Size(max = 20000) String objective,
    @NotNull @Size(max = 20000) String assessment,
    @NotNull @Size(max = 20000) String plan,
    boolean complete,
    @PositiveOrZero long version,
    @NotNull @Size(max = 30) List<@NotNull @Valid DiagnosisInput> diagnoses
) {
    public record DiagnosisInput(@NotBlank @Size(max = 20) String code, boolean principal) {}
    public SoapInput(String subjective, String objective, String assessment, String plan, boolean complete, long version) {
        this(subjective, objective, assessment, plan, complete, version, List.of());
    }
}
