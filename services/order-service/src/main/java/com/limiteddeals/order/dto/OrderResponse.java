package com.limiteddeals.order.dto;

public record OrderResponse(Long orderId, Long productId, int quantity, int remainingStock) {
}
