package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 같은 멱등 키의 첫 요청이 아직 처리 중이다. 잠시 뒤 같은 키로 다시 보내면 결과를 받는다. */
@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException(String key) {
        super("같은 Idempotency-Key의 요청이 처리 중입니다. key=" + key);
    }
}
