package com.limiteddeals.order.domain;

/**
 * 주문 상태 (8단계, Saga).
 * 주문할 때 재고를 먼저 잡아 두고 PENDING_PAYMENT로 시작한다.
 * 결제 서버가 결과를 보내면 PAID(확정) 또는 CANCELLED(취소, 재고 되돌림)로 한 번만 바뀐다.
 */
public enum OrderStatus {
    PENDING_PAYMENT, PAID, CANCELLED
}
