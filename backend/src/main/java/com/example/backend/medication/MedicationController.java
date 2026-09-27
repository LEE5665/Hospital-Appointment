package com.example.backend.medication;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.util.*;

@RestController
@RequestMapping("/api/clinic/medications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','NURSE','DOCTOR')")
public class MedicationController {
    private final MedicationRepository medications;
    public record Result(String code, String name, String manufacturer, String specification,
                         String category, String productCode) {}
    @GetMapping
    @Transactional(readOnly = true)
    public List<Result> search(@RequestParam(name = "query", defaultValue = "") String query) {
        String term = query.strip();
        if (term.isEmpty()) return List.of();
        if (term.length() > 100) throw new IllegalArgumentException("검색어는 100자 이내로 입력하세요.");
        String escaped = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return medications.search(escaped + "%", "%" + escaped.toLowerCase(Locale.ROOT) + "%", PageRequest.of(0, 50))
            .stream().map(m -> new Result(m.getCode(), m.getName(), m.getManufacturer(), m.getSpecification(),
                m.getCategory(), m.getProductCode())).toList();
    }
}
