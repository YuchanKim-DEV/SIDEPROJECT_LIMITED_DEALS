package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 입장권이 없거나 틀렸다. 대기열을 거치지 않은 주문이다. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class EntryTokenRejectedException extends RuntimeException {

    public EntryTokenRejectedException(String message) {
        super(message);
    }
}
