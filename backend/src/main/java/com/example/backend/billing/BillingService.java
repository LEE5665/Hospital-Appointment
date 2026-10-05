package com.example.backend.billing;

import com.example.backend.encounter.entity.Encounter;
import com.example.backend.encounter.repository.EncounterRepository;
import com.example.backend.member.entity.Role;
import com.example.backend.member.repository.MemberRepository;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasAnyRole('ADMIN','NURSE')")
public class BillingService {
    private final EncounterRepository encounters;
    private final PaymentRepository payments;
    private final MemberRepository members;
    public record Input(@NotNull @Min(1) @Max(999999999) Long amount, @NotNull Payment.Method method) {}
    public record Row(Long encounterId, String patientName, String chartNumber, String doctorName,
                      LocalDateTime completedAt, Long amount, Payment.Method method, String receivedByName,
                      LocalDateTime paidAt) {
        static Row from(Encounter e, Payment p) {
            return new Row(e.getId(), e.getPatient().getName(), e.getPatient().getChartNumber(),
                e.getDoctor() == null ? "" : e.getDoctor().getName(), e.getCompletedAt(),
                p == null ? null : p.getAmount(), p == null ? null : p.getMethod(),
                p == null ? "" : p.getReceivedBy().getName(), p == null ? null : p.getPaidAt());
        }
    }
    @Transactional(readOnly = true)
    public List<Row> list(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        var visits = encounters.findByStatusAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscIdAsc(
            Encounter.Status.COMPLETED, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
        if (visits.isEmpty()) return List.of();
        Map<Long, Payment> paid = payments.findByEncounterIdIn(visits.stream().map(Encounter::getId).toList())
            .stream().collect(Collectors.toMap(p -> p.getEncounter().getId(), Function.identity()));
        return visits.stream().map(e -> Row.from(e, paid.get(e.getId()))).toList();
    }
    public Row pay(Long encounterId, Input input, Authentication auth) {
        if (input == null || input.amount() == null || input.amount() < 1 || input.amount() > 999999999 || input.method() == null)
            throw new IllegalArgumentException("금액은 1~999,999,999원, 결제수단은 현금 또는 카드로 입력하세요.");
        var actor = members.findByEmail(auth.getName())
            .filter(m -> m.isActive() && (m.getRole() == Role.ADMIN || m.getRole() == Role.NURSE))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "수납 권한이 없습니다."));
        var encounter = encounters.findLockedById(encounterId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "진료를 찾을 수 없습니다."));
        if (encounter.getStatus() != Encounter.Status.COMPLETED)
            throw new IllegalStateException("진료 완료 후 수납할 수 있습니다.");
        if (payments.existsByEncounterId(encounterId)) throw new IllegalStateException("이미 수납 완료된 진료입니다.");
        return Row.from(encounter, payments.saveAndFlush(new Payment(encounter, input.amount(), input.method(), actor)));
    }
}
