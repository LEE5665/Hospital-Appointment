package com.example.backend.order;

import java.time.LocalDateTime;

public record OrderView(Long id, Long encounterId, Long patientId, String patientName, String chartNumber,
    String itemCode, String itemName, OrderItem.Type type, String instructions, ClinicalOrder.Status status,
    Long requestedById, String requestedByName, LocalDateTime requestedAt,
    Long performedById, String performedByName, LocalDateTime startedAt, LocalDateTime completedAt,
    String result, String cancellationReason, LocalDateTime cancelledAt,
    String reviewedByName, LocalDateTime reviewedAt, long version) {
    public static OrderView from(ClinicalOrder o) {
        var patient = o.getEncounter().getPatient();
        return new OrderView(o.getId(), o.getEncounter().getId(), patient.getId(), patient.getName(), patient.getChartNumber(),
            o.getItem().getCode(), o.getItemName(), o.getType(), o.getInstructions(), o.getStatus(),
            o.getRequestedBy().getId(), o.getRequestedBy().getName(), o.getRequestedAt(),
            o.getPerformedBy() == null ? null : o.getPerformedBy().getId(),
            o.getPerformedBy() == null ? "미지정" : o.getPerformedBy().getName(), o.getStartedAt(), o.getCompletedAt(),
            o.getResult(), o.getCancellationReason(), o.getCancelledAt(),
            o.getReviewedBy() == null ? null : o.getReviewedBy().getName(), o.getReviewedAt(), o.getVersion());
    }
}
