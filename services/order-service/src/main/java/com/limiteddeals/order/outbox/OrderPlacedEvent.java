package com.limiteddeals.order.outbox;

import java.time.Instant;

/** 주문 접수 이벤트 내용. notification-service와 payment-service가 받는다. amount는 결제할 금액. */
public record OrderPlacedEvent(String eventId, Long orderId, Long productId, int quantity, long amount, String userId,
                               Instant occurredAt) {

    public static final String TYPE = "OrderPlaced";
}
