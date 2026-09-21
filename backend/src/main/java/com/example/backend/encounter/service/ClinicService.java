package com.example.backend.encounter.service;

import com.example.backend.encounter.dto.*;
import com.example.backend.appointment.entity.Appointment;
import com.example.backend.appointment.repository.AppointmentRepository;
import com.example.backend.encounter.entity.Encounter;
import com.example.backend.encounter.repository.EncounterRepository;
import com.example.backend.member.entity.*;
import com.example.backend.member.repository.MemberRepository;
import com.example.backend.patient.entity.Patient;
import com.example.backend.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','NURSE','DOCTOR')")
@Transactional
public class ClinicService {
    private final PatientRepository patients;
    private final MemberRepository members;
    private final EncounterRepository encounters;
    private final AppointmentRepository appointments;

    @Transactional(readOnly = true)
    public List<PatientView> patients(String query) {
        return patients.search(query.trim(), org.springframework.data.domain.PageRequest.of(0, 100)).stream().map(PatientView::from).toList();
    }
    public PatientView create(PatientInput input) {
        return PatientView.from(patients.save(Patient.builder().chartNumber("P" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
            .name(input.name().trim()).birthDate(input.birthDate()).gender(input.gender()).phone(input.phone().trim()).address(input.address())
            .allergies(input.allergies()).medicalHistory(input.medicalHistory()).memo(input.memo()).build()));
    }
    public PatientView updatePatient(Long id, PatientInput input) {
        Patient patient = lockedPatient(id);
        patient.updateDetails(input.name().trim(), input.birthDate(), input.gender(), input.phone().trim(),
            input.address(), input.allergies(), input.medicalHistory(), input.memo());
        return PatientView.from(patient);
    }
    @Transactional(readOnly = true)
    public List<DoctorView> doctors() {
        return members.findByRole(Role.DOCTOR).stream().filter(Member::isActive).map(m -> new DoctorView(m.getId(),m.getName(),m.getDepartment())).toList();
    }
    @Transactional(readOnly = true)
    public List<EncounterView> encounters(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return encounters.findByRegisteredAtBetween(day.atStartOfDay(), day.plusDays(1).atStartOfDay()).stream().map(EncounterView::from).toList();
    }
    @Transactional(readOnly = true)
    public EncounterPage queue(LocalDate date, String filter, boolean mine, int page, int size, Authentication auth) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("페이지는 0 이상, 조회 수는 1~100이어야 합니다.");
        List<Encounter.Status> statuses = switch (filter) {
            case "ACTIVE" -> List.of(Encounter.Status.WAITING, Encounter.Status.IN_PROGRESS);
            case "WAITING" -> List.of(Encounter.Status.WAITING);
            case "IN_PROGRESS" -> List.of(Encounter.Status.IN_PROGRESS);
            case "COMPLETED" -> List.of(Encounter.Status.COMPLETED);
            default -> throw new IllegalArgumentException("올바른 진료 상태를 선택해 주세요.");
        };
        Long doctorId = mine ? actor(auth).getId() : null;
        LocalDate day = date == null ? LocalDate.now() : date;
        var result = encounters.findQueue(day.atStartOfDay(), day.plusDays(1).atStartOfDay(), statuses, doctorId,
            org.springframework.data.domain.PageRequest.of(page, size));
        return new EncounterPage(result.getContent().stream().map(EncounterView::from).toList(), page, size, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public EncounterView encounter(Long id) {
        return EncounterView.from(encounters.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "접수를 찾을 수 없습니다.")));
    }
    public EncounterView register(Registration input) {
        Patient patient = lockedPatient(input.patientId());
        if (encounters.existsByPatientIdAndStatusIn(patient.getId(), List.of(Encounter.Status.WAITING, Encounter.Status.IN_PROGRESS)))
            throw new IllegalStateException("이미 대기 또는 진료 중인 접수가 있습니다.");
        Appointment appointment = null;
        Long doctorId = input.doctorId();
        String reason = input.reason();
        if (input.appointmentId() != null) {
            appointment = appointments.findLockedById(input.appointmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
            if (!appointment.getPatient().getId().equals(patient.getId()))
                throw new IllegalArgumentException("선택한 환자의 예약이 아닙니다.");
            appointment.requireBooked();
            if (!appointment.getScheduledAt().toLocalDate().equals(LocalDate.now()))
                throw new IllegalStateException("예약 당일에만 방문 접수로 전환할 수 있습니다.");
            doctorId = appointment.getDoctor() == null ? null : appointment.getDoctor().getId();
            reason = appointment.getReason();
        } else {
            LocalDate today = LocalDate.now();
            if (!appointments.findBookedForPatient(patient.getId(), today.atStartOfDay(), today.plusDays(1).atStartOfDay()).isEmpty())
                throw new IllegalStateException("오늘 예약이 있습니다. 예약 목록을 새로 확인한 후 연결할 예약을 선택해 주세요.");
        }
        Member doctor = doctorId == null ? null : members.findById(doctorId).orElseThrow(() -> new IllegalArgumentException("의사를 찾을 수 없습니다."));
        if (doctor != null && (doctor.getRole() != Role.DOCTOR || !doctor.isActive())) throw new IllegalArgumentException("활성 의사 계정만 배정할 수 있습니다.");
        Encounter encounter = encounters.saveAndFlush(new Encounter(patient, doctor, reason));
        if (appointment != null) appointment.checkIn(encounter);
        return EncounterView.from(encounter);
    }
    @PreAuthorize("hasRole('DOCTOR')")
    public EncounterView start(Long id, Authentication auth) {
        Encounter e = locked(id);
        e.start(actor(auth));
        encounters.flush();
        return EncounterView.from(e);
    }
    @PreAuthorize("hasRole('DOCTOR')")
    public EncounterView save(Long id, NoteInput input, Authentication auth) {
        Encounter e = locked(id);
        if (e.getVersion() != input.version()) throw new IllegalStateException("기록이 변경되었습니다. 새로고침 후 다시 확인해 주세요.");
        e.saveNote(actor(auth), input.content(), input.complete());
        encounters.flush();
        return EncounterView.from(e);
    }
    private Patient lockedPatient(Long id) {
        return patients.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "환자를 찾을 수 없습니다."));
    }

    private Encounter locked(Long id) { return encounters.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"접수를 찾을 수 없습니다.")); }
    private Member actor(Authentication auth) {
        return members.findByEmail(auth.getName()).filter(m -> m.isActive() && m.getRole() == Role.DOCTOR)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,"활성 의사 계정이 필요합니다."));
    }
}
