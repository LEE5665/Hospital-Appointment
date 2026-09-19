package com.example.backend.member.repository;

import com.example.backend.member.entity.Member;
import com.example.backend.member.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findByRole(Role role);

    List<Member> findByRoleAndDepartment(Role role, String department);
}
