package com.example.backend.global.init;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.Charset;

@Service
@RequiredArgsConstructor
public class MedicationMasterImporter {
    private final JdbcTemplate jdbc;
    private final MedicationCsvReader reader;
    @Transactional
    public int initialize(Resource resource, Charset charset) {
        if (Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from medications)", Boolean.class))) return 0;
        var entries = reader.read(resource, charset);
        jdbc.batchUpdate("""
            insert into medications(code,name,manufacturer,specification,category,product_code,ingredient_code)
            values (?,?,?,?,?,?,?)
            """, entries, 500, (ps, e) -> {
                ps.setString(1, e.code()); ps.setString(2, e.name()); ps.setString(3, e.manufacturer());
                ps.setString(4, e.specification()); ps.setString(5, e.category());
                ps.setString(6, e.productCode()); ps.setString(7, e.ingredientCode());
            });
        return entries.size();
    }
}
