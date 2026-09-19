package com.example.backend.member.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Role {
    DOCTOR("의사"),
    NURSE("간호사"),
    ADMIN("관리자");

    private final String description;
}
