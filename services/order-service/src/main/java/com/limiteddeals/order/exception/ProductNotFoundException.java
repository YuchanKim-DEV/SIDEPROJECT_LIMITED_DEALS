package com.limiteddeals.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 없는 상품을 찾으면 404 Not Found로 응답한다. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(Long id) {
        super("상품이 없습니다. id=" + id);
    }
}
