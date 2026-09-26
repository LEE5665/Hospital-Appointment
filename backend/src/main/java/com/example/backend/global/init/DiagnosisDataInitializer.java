package com.example.backend.global.init;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clinic.diagnosis.init.enabled", havingValue = "true", matchIfMissing = true)
public class DiagnosisDataInitializer implements CommandLineRunner {
    private final DiagnosisMasterImporter importer;
    @Value("${clinic.diagnosis.init.resource:classpath:data/배포용 상병마스터.xlsx}")
    private Resource resource;
    @Value("${clinic.diagnosis.init.version:KCD-9}")
    private String version;

    @Override public void run(String... args) {
        var result = importer.initialize(resource, version);
        if (result.skipped()) log.info("진단코드가 이미 존재하므로 상병마스터 초기화를 건너뜁니다.");
        else log.info("상병마스터 초기화 완료: {} 코드 {}개, 명칭 {}개", version, result.codes(), result.terms());
    }
}
