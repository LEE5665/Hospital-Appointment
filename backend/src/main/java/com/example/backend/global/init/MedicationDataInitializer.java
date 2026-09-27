package com.example.backend.global.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import java.nio.charset.Charset;

@Component
@Profile("!test")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "clinic.medication.init.enabled", havingValue = "true", matchIfMissing = true)
public class MedicationDataInitializer implements CommandLineRunner {
    private final MedicationMasterImporter importer;
    @Value("${clinic.medication.init.resource:classpath:data/의약품표준코드.csv}") private Resource resource;
    @Value("${clinic.medication.init.charset:MS949}") private String charset;
    @Override public void run(String... args) {
        int count = importer.initialize(resource, Charset.forName(charset));
        log.info(count == 0 ? "기존 약품 목록이 있어 초기화를 건너뜁니다." : "약품 초기화 완료: {}개", count);
    }
}
