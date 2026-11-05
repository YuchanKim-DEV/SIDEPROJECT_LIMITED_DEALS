package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 같은 멱등 키로 다른 내용을 보냈다. 클라이언트 버그이므로 422로 거절한다. */
@ResponseStatus(HttpStatus.UNPROCESSABLE_CONTENT)
public class IdempotencyKeyMismatchException extends RuntimeException {

    public IdempotencyKeyMismatchException(String key) {
        super("같은 Idempotency-Key로 다른 요청을 보냈습니다. key=" + key);
    }
}
