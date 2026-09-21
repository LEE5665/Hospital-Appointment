package com.example.backend.encounter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record Registration(@NotNull Long patientId, Long doctorId, @Size(max=500) String reason, Long appointmentId) {
    public Registration(Long patientId, Long doctorId, String reason) {
        this(patientId, doctorId, reason, null);
    }
}
