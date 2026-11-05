package com.limiteddeals.order.outbox;

import java.time.Instant;

/** 주문 확정 이벤트 내용. notification-service(나중엔 payment-service)가 받는다. */
public record OrderPlacedEvent(String eventId, Long orderId, Long productId, int quantity, String userId, Instant occurredAt) {

    public static final String TYPE = "OrderPlaced";
}
