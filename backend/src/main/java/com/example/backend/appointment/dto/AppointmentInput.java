package com.example.backend.appointment.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record AppointmentInput(@NotNull Long patientId, Long doctorId,
    @NotNull LocalDateTime scheduledAt, @Size(max=500) String reason) { }
