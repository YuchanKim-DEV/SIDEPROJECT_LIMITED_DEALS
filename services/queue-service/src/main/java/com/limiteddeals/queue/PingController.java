package com.limiteddeals.queue;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 게이트웨이 라우팅 확인용. 대기열 기능이 생기면 지운다.
@RestController
public class PingController {

    @GetMapping("/api/queue/ping")
    public Map<String, String> ping() {
        return Map.of("service", "queue-service");
    }
}
