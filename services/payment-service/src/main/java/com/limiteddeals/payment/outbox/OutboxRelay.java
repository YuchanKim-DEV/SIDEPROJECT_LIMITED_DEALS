package com.limiteddeals.payment.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 릴레이: 아직 안 보낸 이벤트를 주기적으로 읽어 Kafka로 보내고, 보냈다고 표시한다.
 *
 * 보장: 유실 없음(at-least-once). 보내고 "보냄" 표시를 하기 전에 서버가 죽으면 다음에 같은 이벤트를 또 보낸다.
 * 그래서 받는 쪽(order-service)이 상태 전이로 중복을 걸러낸다.
 * Kafka가 죽어 있으면 이벤트는 PENDING으로 남아 있다가, Kafka가 살아나면 순서대로 나간다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "limited-deals.outbox.relay-enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final Counter published;
    private final Counter failed;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${limited-deals.outbox.topic:payment-events}") String topic, MeterRegistry registry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.published = Counter.builder("outbox.published").description("Kafka로 보낸 이벤트 수").register(registry);
        this.failed = Counter.builder("outbox.publish.failed").description("보내기에 실패한 횟수").register(registry);
        Gauge.builder("outbox.pending", () -> repository.countByStatus(OutboxEvent.Status.PENDING))
                .description("아직 Kafka로 보내지 못한 이벤트 수").register(registry);
    }

    @Scheduled(fixedDelayString = "${limited-deals.outbox.relay-interval-ms:300}")
    public void relay() {
        List<OutboxEvent> batch = repository.findByStatusOrderByIdAsc(OutboxEvent.Status.PENDING, PageRequest.of(0, 200));
        for (OutboxEvent event : batch) {
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getMessageKey(), event.getPayload());
                record.headers().add("eventId", event.getEventId().getBytes(StandardCharsets.UTF_8));
                record.headers().add("eventType", event.getEventType().getBytes(StandardCharsets.UTF_8));
                kafkaTemplate.send(record).get(5, TimeUnit.SECONDS); // 브로커가 받았다고 답할 때까지 기다린다(acks=all)
                repository.markSent(event.getId(), Instant.now());
                published.increment();
            } catch (Exception e) {
                repository.increaseAttempts(event.getId());
                failed.increment();
                log.warn("Outbox 발행 실패, 다음 주기에 다시 시도: id={}, {}", event.getId(), e.getMessage());
                return; // 순서를 지키기 위해 이번 주기는 여기서 멈춘다
            }
        }
    }
}
