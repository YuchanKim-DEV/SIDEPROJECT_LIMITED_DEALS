package com.limiteddeals.order.exception;

/** 낙관적 락에서 다른 사람이 먼저 재고를 바꿔서 이번 시도가 실패했다. 호출한 쪽이 다시 시도한다. */
public class OptimisticConflictException extends RuntimeException {

    public OptimisticConflictException(Long productId) {
        super("다른 주문과 충돌했습니다. 다시 시도합니다. productId=" + productId);
    }
}
