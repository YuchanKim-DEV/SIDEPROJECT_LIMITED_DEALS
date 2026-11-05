package com.limiteddeals.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 게이트웨이의 서킷 브레이커: 뒤쪽 서버가 죽었거나(연결 실패) 3초 안에 답하지 않으면 실패로 센다.
 * 최근 10초 동안(최소 100건) 절반이 실패하면 5초 동안 회로를 열어, 그동안은 뒤쪽으로 보내지 않고 바로 503 안내를 돌려준다.
 *
 * 처음에는 "최근 20건" 기준이었다. 초당 1,000건이 오면 0.02초 동안의 기록만 보고 판단하게 되어,
 * 순간적인 지연에도 회로가 열리고 대기열 전체가 막혔다(실험 06). 트래픽이 많은 입구는 시간 기준 창이 맞다.
 */
@Configuration
public class GatewayCircuitBreakerConfig {

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultGatewayCircuitBreaker() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(100)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(5))
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(3)).build())
                .build());
    }
}
