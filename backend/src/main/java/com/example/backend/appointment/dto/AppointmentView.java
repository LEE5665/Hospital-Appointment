package com.example.backend.appointment.dto;

import com.example.backend.appointment.entity.Appointment;
import java.time.LocalDateTime;

public record AppointmentView(Long id, Long patientId, String patientName, String phone, Long doctorId,
    String doctorName, LocalDateTime scheduledAt, String reason, Appointment.Status status, Long encounterId, long version) {
    public static AppointmentView from(Appointment a) {
        return new AppointmentView(a.getId(), a.getPatient().getId(), a.getPatient().getName(), a.getPatient().getPhone(),
            a.getDoctor() == null ? null : a.getDoctor().getId(), a.getDoctor() == null ? "미지정" : a.getDoctor().getName(),
            a.getScheduledAt(), a.getReason(), a.getStatus(), a.getEncounter() == null ? null : a.getEncounter().getId(), a.getVersion());
    }
}
