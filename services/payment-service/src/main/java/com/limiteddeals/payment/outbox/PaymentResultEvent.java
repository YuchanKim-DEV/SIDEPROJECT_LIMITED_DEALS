package com.limiteddeals.payment.outbox;

import java.time.Instant;

/** 결제 결과 이벤트. order-service가 받아 주문을 확정(PaymentCompleted)하거나 취소(PaymentFailed)한다. */
public record PaymentResultEvent(String eventId, String type, Long paymentId, Long orderId, long amount, String reason,
                                 Instant occurredAt) {

    public static final String COMPLETED = "PaymentCompleted";
    public static final String FAILED = "PaymentFailed";
}
