package com.example.backend.global.init;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class DiagnosisMasterImporter {
    private final JdbcTemplate jdbc;
    private final DiagnosisMasterReader reader;

    public record Result(int codes, int terms, boolean skipped) {}

    /** One transaction: failed imports never leave a partially initialized catalog. */
    @Transactional
    public Result initialize(Resource resource, String version) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from diagnosis_codes)", Boolean.class)))
            return new Result(0, 0, true);
        if (version == null || version.isBlank() || version.length() > 30)
            throw new IllegalArgumentException("진단코드 분류 버전을 확인하세요.");
        var entries = reader.read(resource);
        var codes = new LinkedHashMap<String, DiagnosisMasterReader.Entry>();
        entries.forEach(entry -> codes.putIfAbsent(entry.code(), entry));
        jdbc.batchUpdate("""
                insert into diagnosis_codes (code, korean_name, english_name, complete_code,
                    principal_diagnosis_allowed, infectious_disease_class, sex_restriction,
                    minimum_age, maximum_age, medicine_type, classification_version, source_file)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, codes.values(), 500, (ps, entry) -> {
            ps.setString(1, entry.code());
            ps.setString(2, entry.koreanName());
            ps.setString(3, entry.englishName());
            ps.setBoolean(4, entry.completeCode());
            ps.setBoolean(5, entry.principalAllowed());
            ps.setString(6, entry.infectionClass());
            ps.setString(7, entry.sexRestriction());
            ps.setObject(8, entry.minimumAge(), java.sql.Types.INTEGER);
            ps.setObject(9, entry.maximumAge(), java.sql.Types.INTEGER);
            ps.setString(10, entry.medicineType());
            ps.setString(11, version);
            ps.setString(12, resource.getFilename());
        });
        jdbc.batchUpdate("""
                insert into diagnosis_terms (diagnosis_code, korean_name, english_name, source_row)
                values (?, ?, ?, ?)
                """, entries, 500, (ps, entry) -> {
            ps.setString(1, entry.code());
            ps.setString(2, entry.koreanName());
            ps.setString(3, entry.englishName());
            ps.setInt(4, entry.sourceRow());
        });
        return new Result(codes.size(), entries.size(), false);
    }
}
