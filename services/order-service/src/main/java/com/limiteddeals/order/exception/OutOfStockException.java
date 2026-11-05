package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 남은 재고보다 많이 사려고 하면 409 Conflict로 응답한다. */
@ResponseStatus(HttpStatus.CONFLICT)
public class OutOfStockException extends RuntimeException {

    public OutOfStockException(Long productId, int stock, int quantity) {
        super("재고가 부족합니다. productId=" + productId + ", 남은 재고=" + stock + ", 요청=" + quantity);
    }
}
