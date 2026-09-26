package com.example.backend.diagnosis;

import com.example.backend.diagnosis.entity.DiagnosisCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/clinic/diagnoses")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DOCTOR','NURSE','ADMIN')")
public class DiagnosisController {
    private final DiagnosisRepository repository;
    public record Result(String code, String name, String englishName, String classificationVersion,
                         boolean principalDiagnosisAllowed) {
        static Result from(DiagnosisCode d) {
            return new Result(d.getCode(), d.getKoreanName(), d.getEnglishName(),
                d.getClassificationVersion(), d.isPrincipalDiagnosisAllowed());
        }
    }
    @GetMapping
    @Transactional(readOnly = true)
    public List<Result> search(@RequestParam(name = "query", defaultValue = "") String query) {
        String term = query.strip();
        if (term.isEmpty()) return List.of();
        if (term.length() > 100) throw new IllegalArgumentException("진단 검색어는 100자 이내로 입력해 주세요.");
        String code = term.replace(".", "").toUpperCase(Locale.ROOT);
        return repository.search(escape(code) + "%", "%" + escape(term.toLowerCase(Locale.ROOT)) + "%",
                code, PageRequest.of(0, 50)).stream().map(Result::from).toList();
    }
    private String escape(String text) { return text.replace("!", "!!").replace("%", "!%").replace("_", "!_"); }
}
