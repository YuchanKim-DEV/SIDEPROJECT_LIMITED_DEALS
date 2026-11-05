package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 낙관적 락 재시도를 정해진 횟수만큼 해도 계속 충돌했다. 잠시 뒤 다시 시도하라고 503으로 응답한다. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class TooManyConflictsException extends RuntimeException {

    public TooManyConflictsException(Long productId, int attempts) {
        super("주문이 몰려 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요. productId=" + productId + ", 시도=" + attempts);
    }
}
