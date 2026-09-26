package com.example.backend.appointment.controller;

import com.example.backend.appointment.dto.*;
import com.example.backend.appointment.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clinic/appointments")
@RequiredArgsConstructor
public class AppointmentController {
    private final AppointmentService appointments;
    @GetMapping
    public List<AppointmentView> list(@RequestParam(name = "date", required = false) LocalDate date) { return appointments.list(date); }
    @GetMapping("/patient/{patientId}/today")
    public List<AppointmentView> todayForPatient(@PathVariable("patientId") Long patientId) { return appointments.todayForPatient(patientId); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AppointmentView create(@Valid @RequestBody AppointmentInput input) { return appointments.create(input); }
    @PutMapping("/{id}")
    public AppointmentView update(@PathVariable("id") Long id, @Valid @RequestBody AppointmentUpdate input) { return appointments.update(id, input); }
    @PostMapping("/{id}/cancel")
    public AppointmentView cancel(@PathVariable("id") Long id) { return appointments.cancel(id); }
    @PostMapping("/{id}/check-in")
    public AppointmentView checkIn(@PathVariable("id") Long id) { return appointments.checkIn(id); }
}
