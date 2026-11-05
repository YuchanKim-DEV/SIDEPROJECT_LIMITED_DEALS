package com.limiteddeals.notification;

import com.limiteddeals.notification.domain.InboxEventRepository;
import com.limiteddeals.notification.domain.NotificationRepository;
import com.limiteddeals.notification.service.OrderEventHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 4단계: 같은 이벤트가 여러 번 와도 알림은 한 번만 보낸다(Inbox). 처리에 실패하면 아무것도 남지 않는다. */
@SpringBootTest
class OrderEventHandlerTest {

    @Autowired OrderEventHandler handler;
    @Autowired InboxEventRepository inboxRepository;
    @Autowired NotificationRepository notificationRepository;

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    private static String event(String eventId, long orderId, long productId) {
        return """
                {"eventId":"%s","orderId":%d,"productId":%d,"quantity":1,"userId":"u1","occurredAt":"%s"}
                """.formatted(eventId, orderId, productId, Instant.now());
    }

    @Test
    void 같은_이벤트를_세_번_받아도_알림은_한_번이다() {
        String payload = event("e-1", 10, 1);

        assertThat(handler.handle(payload)).isTrue();
        assertThat(handler.handle(payload)).isFalse();
        assertThat(handler.handle(payload)).isFalse();

        assertThat(notificationRepository.count()).isEqualTo(1);
        assertThat(inboxRepository.count()).isEqualTo(1);
    }

    @Test
    void 처리에_실패하면_Inbox에도_남지_않아서_다시_받으면_다시_처리한다() {
        assertThatThrownBy(() -> handler.handle(event("e-2", 11, 999))).isInstanceOf(IllegalStateException.class);

        assertThat(inboxRepository.existsByEventId("e-2")).isFalse();
        assertThat(notificationRepository.count()).isZero();
    }
}
