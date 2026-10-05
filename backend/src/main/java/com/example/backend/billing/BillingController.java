package com.example.backend.billing;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clinic/billing")
@RequiredArgsConstructor
public class BillingController {
    private final BillingService service;
    @GetMapping
    public List<BillingService.Row> list(@RequestParam(name = "date", required = false) LocalDate date) {
        return service.list(date);
    }
    @PostMapping("/{encounterId}/payment")
    @ResponseStatus(HttpStatus.CREATED)
    public BillingService.Row pay(@PathVariable("encounterId") Long encounterId,
        @Valid @RequestBody BillingService.Input input, Authentication auth) {
        return service.pay(encounterId, input, auth);
    }
}
