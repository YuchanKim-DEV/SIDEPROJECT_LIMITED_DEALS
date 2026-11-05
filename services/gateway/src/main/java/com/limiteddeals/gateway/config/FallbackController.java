package com.limiteddeals.gateway.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR;

/**
 * 뒤쪽 서버가 죽었거나 느릴 때(서킷 브레이커) 500 대신 503과 안내 문구를 돌려준다.
 * 왜 실패했는지(원래 예외)를 로그로 남긴다. 회로가 열려서 막힌 경우(CallNotPermitted)는 너무 많아서 남기지 않는다.
 */
@Slf4j
@RestController
public class FallbackController {

    @RequestMapping("/fallback/{service}")
    public ResponseEntity<Map<String, String>> fallback(@PathVariable String service, ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        if (cause != null && !(cause instanceof CallNotPermittedException)) {
            log.warn("{} 호출 실패로 503 안내: {}: {}", service, cause.getClass().getName(), cause.getMessage());
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "5")
                .body(Map.of("service", service, "message", "일시적으로 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요."));
    }
}
