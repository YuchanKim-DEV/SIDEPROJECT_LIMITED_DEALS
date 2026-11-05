package com.limiteddeals.order.dto;

/** 주문 결과와, 그것이 이전 요청의 결과를 다시 돌려준 것(replayed)인지 여부. */
public record PlacedOrder(OrderResponse response, boolean replayed) {
}
