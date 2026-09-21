package com.example.backend.encounter.dto;

import java.util.List;

public record EncounterPage(List<EncounterView> items, int page, int size, long totalElements, int totalPages) { }
