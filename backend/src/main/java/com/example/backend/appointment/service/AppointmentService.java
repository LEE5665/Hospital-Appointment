package com.example.backend.appointment.service;

import com.example.backend.appointment.dto.*;
import com.example.backend.appointment.entity.Appointment;
import com.example.backend.appointment.repository.AppointmentRepository;
import com.example.backend.encounter.dto.Registration;
import com.example.backend.encounter.service.ClinicService;
import com.example.backend.member.entity.*;
import com.example.backend.member.repository.MemberRepository;
import com.example.backend.patient.repository.PatientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@PreAuthorize("hasAnyRole('ADMIN','NURSE','DOCTOR')")
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final PatientRepository patients;
    private final MemberRepository members;
    private final ClinicService clinic;

    @Transactional(readOnly = true)
    public List<AppointmentView> list(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now() : date;
        return appointments.findForDate(day.atStartOfDay(), day.plusDays(1).atStartOfDay()).stream().map(AppointmentView::from).toList();
    }
    @Transactional(readOnly = true)
    public List<AppointmentView> todayForPatient(Long patientId) {
        LocalDate today = LocalDate.now();
        return appointments.findBookedForPatient(patientId, today.atStartOfDay(), today.plusDays(1).atStartOfDay())
            .stream().map(AppointmentView::from).toList();
    }
    public AppointmentView create(AppointmentInput input) {
        LocalDateTime time = input.scheduledAt();
        validateTime(time);
        var patient = patients.findLockedById(input.patientId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "환자를 찾을 수 없습니다."));
        Member doctor = input.doctorId() == null ? null : members.findLockedById(input.doctorId()).orElseThrow(() -> new IllegalArgumentException("의사를 찾을 수 없습니다."));
        if (doctor != null && (doctor.getRole() != Role.DOCTOR || !doctor.isActive())) throw new IllegalArgumentException("활성 의사만 예약할 수 있습니다.");
        if (appointments.existsConflict(patient.getId(), input.doctorId(), time))
            throw new IllegalStateException("해당 시간에 환자 또는 담당 의사의 예약이 이미 있습니다.");
        return AppointmentView.from(appointments.saveAndFlush(new Appointment(patient, doctor, time, input.reason())));
    }
    public AppointmentView update(Long id, AppointmentUpdate input) {
        validateTime(input.scheduledAt());
        Long patientId = appointments.findPatientIdById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
        // Keep the same patient -> appointment lock order as check-in.
        patients.findLockedById(patientId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "환자를 찾을 수 없습니다."));
        var appointment = locked(id);
        appointment.requireBooked();
        if (input.version() == null || appointment.getVersion() != input.version())
            throw new IllegalStateException("다른 사용자가 예약을 변경했습니다. 새로고침 후 다시 수정해 주세요.");
        Member doctor = input.doctorId() == null ? null : members.findLockedById(input.doctorId())
            .orElseThrow(() -> new IllegalArgumentException("의사를 찾을 수 없습니다."));
        if (doctor != null && (doctor.getRole() != Role.DOCTOR || !doctor.isActive()))
            throw new IllegalArgumentException("활성 의사만 예약할 수 있습니다.");
        if (appointments.existsConflictExcluding(patientId, input.doctorId(), input.scheduledAt(), id))
            throw new IllegalStateException("해당 시간에 환자 또는 담당 의사의 예약이 이미 있습니다.");
        appointment.reschedule(doctor, input.scheduledAt(), input.reason());
        appointments.flush();
        return AppointmentView.from(appointment);
    }
    private void validateTime(LocalDateTime time) {
        if (time == null || !time.isAfter(LocalDateTime.now())) throw new IllegalArgumentException("현재 이후의 날짜와 시간을 선택해 주세요.");
        if (time.getMinute() % 30 != 0 || time.getSecond() != 0 || time.getNano() != 0)
            throw new IllegalArgumentException("예약은 30분 단위로 선택해 주세요.");
    }
    public AppointmentView cancel(Long id) {
        var appointment = locked(id);
        appointment.cancel();
        return AppointmentView.from(appointment);
    }
    public AppointmentView checkIn(Long id) {
        // All check-ins acquire the patient lock before the appointment lock.
        Long patientId = appointments.findPatientIdById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
        clinic.register(new Registration(patientId, null, null, id));
        return AppointmentView.from(appointments.findById(id).orElseThrow());
    }
    private Appointment locked(Long id) {
        return appointments.findLockedById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "예약을 찾을 수 없습니다."));
    }
}
