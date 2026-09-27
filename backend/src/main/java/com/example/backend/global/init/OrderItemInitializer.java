package com.example.backend.global.init;

import com.example.backend.order.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class OrderItemInitializer implements CommandLineRunner {
    private final OrderItemRepository items;
    @Override @Transactional public void run(String... args) {
        if (items.count() != 0) return;
        // Local demonstration catalog, not insurance billing codes.
        items.saveAll(List.of(
            new OrderItem("LOCAL-CBC","일반 혈액검사",OrderItem.Type.TEST),
            new OrderItem("LOCAL-UA","소변검사",OrderItem.Type.TEST),
            new OrderItem("LOCAL-CXR","흉부 X선 검사",OrderItem.Type.TEST),
            new OrderItem("LOCAL-ECG","심전도 검사",OrderItem.Type.TEST),
            new OrderItem("LOCAL-DRESSING","상처 드레싱",OrderItem.Type.PROCEDURE),
            new OrderItem("LOCAL-IRRIGATION","상처 세척",OrderItem.Type.PROCEDURE),
            new OrderItem("LOCAL-SUTURE-REMOVAL","봉합사 제거",OrderItem.Type.PROCEDURE)));
    }
}
