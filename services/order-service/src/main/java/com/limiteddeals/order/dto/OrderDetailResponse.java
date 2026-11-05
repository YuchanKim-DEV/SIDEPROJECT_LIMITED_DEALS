package com.limiteddeals.order.dto;

import com.limiteddeals.order.domain.Order;
import com.limiteddeals.order.domain.OrderStatus;

/** 주문 조회 결과. 결제가 끝났는지(status) 확인할 때 쓴다. */
public record OrderDetailResponse(Long orderId, Long productId, int quantity, Long amount, OrderStatus status, String userId) {

    public static OrderDetailResponse from(Order order) {
        return new OrderDetailResponse(order.getId(), order.getProductId(), order.getQuantity(), order.getAmount(),
                order.getStatus(), order.getUserId());
    }
}
