package com.example.backend.encounter.dto;

import com.example.backend.encounter.entity.Encounter;
import java.time.LocalDateTime;

public record EncounterView(Long id, Long patientId, String patientName, String chartNumber, Long doctorId,
    String doctorName, Encounter.Status status, LocalDateTime registeredAt, String reason, String note, long version) {
    public static EncounterView from(Encounter e) {
        return new EncounterView(e.getId(), e.getPatient().getId(), e.getPatient().getName(), e.getPatient().getChartNumber(),
            e.getDoctor() == null ? null : e.getDoctor().getId(), e.getDoctor() == null ? "미지정" : e.getDoctor().getName(),
            e.getStatus(), e.getRegisteredAt(), e.getReason(), e.getNote(), e.getVersion());
    }
}
