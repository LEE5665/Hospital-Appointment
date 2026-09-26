package com.example.backend.encounter.controller;

import com.example.backend.encounter.dto.*;
import com.example.backend.encounter.service.ClinicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clinic")
@RequiredArgsConstructor
public class ClinicController {
    private final ClinicService clinicService;

    @GetMapping("/patients")
    public List<PatientView> patients(@RequestParam(name = "query", defaultValue = "") String query) {
        return clinicService.patients(query);
    }

    @PostMapping("/patients")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientView create(@Valid @RequestBody PatientInput input) {
        return clinicService.create(input);
    }

    @PutMapping("/patients/{id}")
    public PatientView updatePatient(@PathVariable("id") Long id, @Valid @RequestBody PatientInput input) {
        return clinicService.updatePatient(id, input);
    }

    @GetMapping("/doctors")
    public List<DoctorView> doctors() {
        return clinicService.doctors();
    }

    @GetMapping("/encounters")
    public List<EncounterView> encounters(@RequestParam(name = "date", required = false) LocalDate date) {
        return clinicService.encounters(date);
    }

    @PostMapping("/encounters")
    @ResponseStatus(HttpStatus.CREATED)
    public EncounterView register(@Valid @RequestBody Registration input) {
        return clinicService.register(input);
    }
    @GetMapping("/encounters/queue")
    public EncounterPage queue(@RequestParam(name = "date", required = false) LocalDate date,
        @RequestParam(name = "filter", defaultValue = "ACTIVE") String filter, @RequestParam(name = "mine", defaultValue = "false") boolean mine,
        @RequestParam(name = "page", defaultValue = "0") int page, @RequestParam(name = "size", defaultValue = "25") int size, Authentication auth) {
        return clinicService.queue(date, filter, mine, page, size, auth);
    }
    @GetMapping("/encounters/{id}")
    public EncounterView encounter(@PathVariable("id") Long id) { return clinicService.encounter(id); }

    @PostMapping("/encounters/{id}/start")
    public EncounterView start(@PathVariable("id") Long id, Authentication auth) {
        return clinicService.start(id, auth);
    }

    @PutMapping("/encounters/{id}/soap")
    public EncounterView save(@PathVariable("id") Long id, @Valid @RequestBody SoapInput input, Authentication auth) {
        return clinicService.save(id, input, auth);
    }
}
