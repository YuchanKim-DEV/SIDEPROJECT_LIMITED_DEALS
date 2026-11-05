package com.limiteddeals.gateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 뒤쪽 서버가 죽었거나 느릴 때(서킷 브레이커) 500 대신 503과 안내 문구를 돌려준다. */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<Map<String, String>> fallback(@PathVariable String service) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(Map.of("service", service, "message", "일시적으로 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
