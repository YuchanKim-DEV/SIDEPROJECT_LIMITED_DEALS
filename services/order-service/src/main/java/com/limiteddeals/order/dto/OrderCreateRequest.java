package com.limiteddeals.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderCreateRequest(
        @NotNull Long productId,
        @Positive int quantity
) {
}
