package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 없는 주문을 찾으면 404 Not Found로 응답한다. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long id) {
        super("주문이 없습니다. id=" + id);
    }
}
