package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 대기열 서버가 응답하지 않아 입장권을 확인할 수 없다. 확인 없이 받지 않고 503으로 거절한다. */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class QueueUnavailableException extends RuntimeException {

    public QueueUnavailableException() {
        super("대기열 서버를 확인할 수 없어 주문을 받을 수 없습니다. 잠시 뒤 다시 시도해 주세요.");
    }
}
