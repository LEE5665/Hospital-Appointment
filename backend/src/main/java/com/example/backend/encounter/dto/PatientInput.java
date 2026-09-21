package com.example.backend.encounter.dto;

import com.example.backend.patient.entity.Gender;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record PatientInput(@NotBlank(message="환자 이름을 입력해 주세요.") @Size(max=50) String name,
    @NotNull(message="생년월일을 입력해 주세요.") @PastOrPresent(message="생년월일은 오늘 이후일 수 없습니다.") LocalDate birthDate,
    @NotNull(message="성별을 선택해 주세요.") Gender gender, @NotBlank(message="연락처를 입력해 주세요.") @Size(max=20) String phone, @Size(max=255) String address,
    @Size(max=4000) String allergies, @Size(max=4000) String medicalHistory, @Size(max=4000) String memo) {}
