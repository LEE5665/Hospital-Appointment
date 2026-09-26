package com.example.backend.global.init;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Discard the retired free-text column; Hibernate update does not drop removed columns. */
@Component
@Order(0)
@RequiredArgsConstructor
public class SoapSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    @Override public void run(String... args) {
        jdbc.execute("alter table encounters drop column if exists note");
    }
}
