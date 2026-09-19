package com.example.backend.global.init;

import com.example.backend.member.entity.Member;
import com.example.backend.member.entity.Role;
import com.example.backend.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (memberRepository.count() > 0) {
            log.info("계정 데이터가 이미 존재하므로 초기화를 건너뜁니다.");
            return;
        }

        log.info("초기 병원 계정(의사, 간호사, 관리자)을 생성합니다...");

        String defaultPassword = passwordEncoder.encode("password123");

        Member doctor = Member.builder()
                .email("doctor@hospital.com")
                .password(defaultPassword)
                .name("김의사")
                .phone("010-1234-5678")
                .role(Role.DOCTOR)
                .department("내과")
                .licenseNumber("DOC-001")
                .active(true)
                .build();

        Member nurse = Member.builder()
                .email("nurse@hospital.com")
                .password(defaultPassword)
                .name("이간호사")
                .phone("010-8765-4321")
                .role(Role.NURSE)
                .department("외래간호팀")
                .licenseNumber("NUR-001")
                .active(true)
                .build();

        Member admin = Member.builder()
                .email("admin@hospital.com")
                .password(defaultPassword)
                .name("박관리자")
                .phone("010-1111-2222")
                .role(Role.ADMIN)
                .department("원무과")
                .licenseNumber("ADM-001")
                .active(true)
                .build();

        memberRepository.saveAll(List.of(doctor, nurse, admin));

        log.info("초기 계정 생성이 완료되었습니다!");
        log.info("1. 의사 계정: doctor@hospital.com / password123 (내과)");
        log.info("2. 간호사 계정: nurse@hospital.com / password123 (외래간호팀)");
        log.info("3. 관리자 계정: admin@hospital.com / password123 (원무과)");
    }
}
