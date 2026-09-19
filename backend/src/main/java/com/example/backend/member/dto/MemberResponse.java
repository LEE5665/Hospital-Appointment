package com.example.backend.member.dto;

import com.example.backend.member.entity.Member;
import com.example.backend.member.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MemberResponse {

    private Long id;
    private String email;
    private String name;
    private String phone;
    private Role role;
    private String roleDescription;
    private String department;
    private String licenseNumber;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .email(member.getEmail())
                .name(member.getName())
                .phone(member.getPhone())
                .role(member.getRole())
                .roleDescription(member.getRole().getDescription())
                .department(member.getDepartment())
                .licenseNumber(member.getLicenseNumber())
                .build();
    }
}
