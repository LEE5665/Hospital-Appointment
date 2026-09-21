package com.example.backend.appointment.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record AppointmentUpdate(Long doctorId, @NotNull LocalDateTime scheduledAt,
    @Size(max=500) String reason, @NotNull @PositiveOrZero Long version) { }
