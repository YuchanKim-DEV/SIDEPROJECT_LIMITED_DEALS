package com.limiteddeals.notification.service;

import com.limiteddeals.notification.domain.InboxEvent;
import com.limiteddeals.notification.domain.InboxEventRepository;
import com.limiteddeals.notification.domain.Notification;
import com.limiteddeals.notification.domain.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * 주문 이벤트 처리(멱등 컨슈머).
 * 1) Inbox에 이미 있는 eventId면 건너뛴다(중복 메시지).
 * 2) 없으면 Inbox 기록 + 알림 저장을 한 트랜잭션으로 한다.
 * 처리 중 예외가 나면 트랜잭션이 취소되고, 리스너의 에러 핸들러가 몇 번 재시도한 뒤 DLQ로 보낸다.
 */
@Slf4j
@Service
public class OrderEventHandler {

    private final InboxEventRepository inboxRepository;
    private final NotificationRepository notificationRepository;
    private final JsonMapper jsonMapper;
    private final Set<Long> failingProductIds;
    private final Counter processed;
    private final Counter duplicates;

    public OrderEventHandler(InboxEventRepository inboxRepository, NotificationRepository notificationRepository,
                             JsonMapper jsonMapper, MeterRegistry registry,
                             @Value("${limited-deals.notification.failing-product-ids:}") Set<Long> failingProductIds) {
        this.inboxRepository = inboxRepository;
        this.notificationRepository = notificationRepository;
        this.jsonMapper = jsonMapper;
        this.failingProductIds = failingProductIds;
        this.processed = Counter.builder("notification.processed").description("처리한 주문 이벤트").register(registry);
        this.duplicates = Counter.builder("notification.duplicates").description("중복이라 건너뛴 이벤트").register(registry);
    }

    /** @return 새로 처리했으면 true, 중복이라 건너뛰었으면 false */
    @Transactional
    public boolean handle(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        String eventId = event.get("eventId").asString();
        long productId = event.get("productId").asLong();

        if (inboxRepository.existsByEventId(eventId)) {
            duplicates.increment();
            log.info("중복 이벤트 건너뜀: eventId={}", eventId);
            return false;
        }
        if (failingProductIds.contains(productId)) {
            // 실험용 독이 든 메시지(poison message): 이 상품의 이벤트는 처리할 때마다 실패한다
            throw new IllegalStateException("처리할 수 없는 이벤트(실험): productId=" + productId);
        }
        inboxRepository.save(new InboxEvent(eventId, "OrderPlaced"));
        long orderId = event.get("orderId").asLong();
        String userId = event.hasNonNull("userId") ? event.get("userId").asString() : null;
        long delay = Duration.between(Instant.parse(event.get("occurredAt").asString()), Instant.now()).toMillis();
        notificationRepository.save(new Notification(eventId, orderId, productId, userId,
                "주문 " + orderId + "번이 확정되었습니다.", delay));
        processed.increment();
        return true;
    }
}
