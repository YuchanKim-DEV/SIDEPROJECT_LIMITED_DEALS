package com.limiteddeals.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** 상품 등록 요청 본문. 규칙에 어긋나면 400 Bad Request로 거절된다. */
public record ProductCreateRequest(
        @NotBlank String name,
        @Positive long price,
        @PositiveOrZero int stock
) {
}
