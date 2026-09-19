package com.example.backend.member.entity;

import com.example.backend.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(length = 50)
    private String department; // 의사: 진료과(내과, 외과 등) / 간호사: 소속 병동

    @Column(length = 50)
    private String licenseNumber; // 의사/간호사 면허 번호

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    public void updateProfile(String name, String phone, String department, String licenseNumber) {
        this.name = name;
        this.phone = phone;
        this.department = department;
        this.licenseNumber = licenseNumber;
    }
}
