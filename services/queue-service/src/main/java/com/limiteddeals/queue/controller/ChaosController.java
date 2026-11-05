package com.limiteddeals.queue.controller;

import com.limiteddeals.queue.service.ChaosSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 장애 실험용 스위치: 입장권 확인 응답을 일부러 늦춘다. CHAOS_ENABLED=true로 띄웠을 때만 생긴다(운영에서는 꺼 둔다).
 */
@RestController
@RequestMapping("/api/queue/admin/chaos")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "limited-deals.queue.chaos-enabled", havingValue = "true")
public class ChaosController {

    private final ChaosSettings chaos;

    @PostMapping
    public Map<String, Long> set(@RequestParam long validateDelayMs) {
        chaos.setValidateDelayMs(validateDelayMs);
        return Map.of("validateDelayMs", chaos.getValidateDelayMs());
    }
}
