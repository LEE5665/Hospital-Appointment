package com.example.backend.member.repository;

import com.example.backend.member.entity.Member;
import com.example.backend.member.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select m from Member m where m.id = :id")
    Optional<Member> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findByRole(Role role);

    List<Member> findByRoleAndDepartment(Role role, String department);
}
