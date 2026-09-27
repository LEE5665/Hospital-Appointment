package com.example.backend.encounter.dto;

import com.example.backend.encounter.entity.Encounter;
import java.time.LocalDateTime;
import java.util.List;

public record EncounterHistoryPage(List<Visit> items, int page, int totalPages, long totalElements) {
    public record Visit(Long id, LocalDateTime registeredAt, LocalDateTime startedAt, LocalDateTime completedAt,
                        String doctorName, Encounter.Status status, String reason) {
        public static Visit from(Encounter e) {
            return new Visit(e.getId(),e.getRegisteredAt(),e.getStartedAt(),e.getCompletedAt(),
                e.getDoctor() == null ? "미지정" : e.getDoctor().getName(),e.getStatus(),e.getReason());
        }
    }
}
