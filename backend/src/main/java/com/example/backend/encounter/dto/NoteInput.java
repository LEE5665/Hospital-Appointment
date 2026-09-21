package com.example.backend.encounter.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NoteInput(@NotNull @Size(max=20000) String content, boolean complete, long version) {}
