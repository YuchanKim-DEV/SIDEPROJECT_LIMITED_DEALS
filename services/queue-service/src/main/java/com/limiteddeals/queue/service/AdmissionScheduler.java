package com.limiteddeals.queue.service;

import com.limiteddeals.queue.config.QueueProperties;
import com.limiteddeals.queue.domain.QueueKeys;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 1초마다 줄 맨 앞에서 admitPerSecond명씩 입장시킨다.
 * queue-service를 여러 대 띄우면 각자 입장시켜서 입장 속도가 대수만큼 늘어난다.
 * 그래서 Redis 잠금(SET NX PX)을 잡은 한 대만 이번 차례를 실행한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "limited-deals.queue.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class AdmissionScheduler {

    private final QueueService queueService;
    private final QueueProperties properties;
    private final StringRedisTemplate redis;
    private final Counter admittedCounter;

    public AdmissionScheduler(QueueService queueService, QueueProperties properties, StringRedisTemplate redis,
                              MeterRegistry meterRegistry) {
        this.queueService = queueService;
        this.properties = properties;
        this.redis = redis;
        this.admittedCounter = Counter.builder("queue.admitted").description("입장권을 받은 인원").register(meterRegistry);
    }

    @Scheduled(fixedRate = 1000)
    public void admitNextGroup() {
        Boolean leader = redis.opsForValue().setIfAbsent(QueueKeys.SCHEDULER_LOCK, "1", Duration.ofMillis(900));
        if (!Boolean.TRUE.equals(leader)) {
            return; // 다른 서버가 이번 차례를 맡았다
        }
        for (String productId : queueService.activeProductIds()) {
            int admitted = queueService.admit(Long.parseLong(productId), properties.admitPerSecond());
            if (admitted > 0) {
                admittedCounter.increment(admitted);
                log.info("입장: productId={}, {}명", productId, admitted);
            }
        }
    }
}
