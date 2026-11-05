package com.limiteddeals.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 대기열 서버 호출용 서킷 브레이커.
 * 최근 20번 중 절반 이상이 실패하거나 0.3초보다 느리면 회로를 연다(OPEN). 열린 동안은 호출하지 않고 바로 거절한다.
 * 5초 뒤 3번만 시험 삼아 호출해 보고(HALF_OPEN), 괜찮으면 다시 닫는다(CLOSED).
 * 호출 하나는 1초를 넘기면 포기한다(시간 제한). 연결·응답 제한(300ms·500ms)이 먼저 걸리고, 이건 마지막 안전장치다.
 */
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> queueCircuitBreaker() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowType(SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .slowCallDurationThreshold(Duration.ofMillis(300))
                        .slowCallRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(1)).build())
                .build(), "queue-service");
    }
}
