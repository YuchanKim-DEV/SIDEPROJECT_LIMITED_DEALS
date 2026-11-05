package com.limiteddeals.queue.service;

import com.limiteddeals.queue.domain.QueueKeys;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 대시보드용 지표: 모든 상품의 대기 인원 합계(queue.waiting.total). 값을 읽을 때마다 Redis에서 센다. */
@Component
public class QueueMetrics {

    public QueueMetrics(MeterRegistry registry, QueueService queueService, StringRedisTemplate redis) {
        Gauge.builder("queue.waiting.total", () -> {
                    try {
                        return queueService.activeProductIds().stream()
                                .map(id -> redis.opsForZSet().zCard(QueueKeys.waiting(Long.parseLong(id))))
                                .mapToLong(size -> size == null ? 0 : size)
                                .sum();
                    } catch (RuntimeException redisDown) {
                        return Double.NaN; // Redis가 죽어도 지표 수집 때문에 서버가 죽지 않게
                    }
                })
                .description("모든 상품의 대기 인원 합계")
                .register(registry);
    }
}
