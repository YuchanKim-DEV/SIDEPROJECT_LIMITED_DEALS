package com.limiteddeals.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 주문 요청 본문. 한정판이라 한 번에 최대 10개까지(한 사람이 재고를 쓸어 가지 못하게). 어기면 400. */
public record OrderCreateRequest(
        @NotNull Long productId,
        @Positive @Max(10) int quantity
) {
}
