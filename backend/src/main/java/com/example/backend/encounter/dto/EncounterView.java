package com.example.backend.encounter.dto;

import com.example.backend.encounter.entity.Encounter;
import java.time.LocalDateTime;
import java.util.List;

public record EncounterView(Long id, Long patientId, String patientName, String chartNumber, Long doctorId,
    String doctorName, Encounter.Status status, LocalDateTime registeredAt, String reason,
    String subjective, String objective, String assessment, String plan, long version, List<DiagnosisView> diagnoses) {
    public record DiagnosisView(String code, String name, String classificationVersion, boolean principal,
                                boolean principalDiagnosisAllowed) {}
    public static EncounterView from(Encounter e) {
        return new EncounterView(e.getId(), e.getPatient().getId(), e.getPatient().getName(), e.getPatient().getChartNumber(),
            e.getDoctor() == null ? null : e.getDoctor().getId(), e.getDoctor() == null ? "미지정" : e.getDoctor().getName(),
            e.getStatus(), e.getRegisteredAt(), e.getReason(), text(e.getSubjective()), text(e.getObjective()),
            text(e.getAssessment()), text(e.getPlan()), e.getVersion(), e.getDiagnoses().stream()
                .map(d -> new DiagnosisView(d.getDiagnosis().getCode(), d.getName(), d.getClassificationVersion(),
                    d.isPrincipal(), d.isPrincipalDiagnosisAllowed())).toList());
    }
    private static String text(String value) { return value == null ? "" : value; }
}
