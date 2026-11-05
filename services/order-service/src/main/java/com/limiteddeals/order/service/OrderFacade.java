package com.limiteddeals.order.service;

import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.exception.OptimisticConflictException;
import com.limiteddeals.order.exception.TooManyConflictsException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 컨트롤러가 부르는 주문 입구. 트랜잭션 바깥에서 해야 하는 일(재시도 등)을 맡는다.
 * 재시도는 반드시 트랜잭션 "바깥"에서 해야 한다. 실패한 트랜잭션은 되돌려야 하므로, 매번 새 트랜잭션으로 다시 시작한다.
 */
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;

    @Value("${limited-deals.order.optimistic.max-attempts:50}")
    private int maxAttempts;

    public OrderResponse place(OrderCreateRequest request, StockStrategy strategy) {
        if (strategy != StockStrategy.OPTIMISTIC) {
            return orderService.place(request, strategy);
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return orderService.place(request, strategy); // 시도마다 새 트랜잭션
            } catch (OptimisticConflictException conflict) {
                backOff(attempt);
            }
        }
        throw new TooManyConflictsException(request.productId(), maxAttempts);
    }

    /** 충돌한 요청들이 똑같은 순간에 다시 부딪히지 않도록, 조금씩 다르게 쉬었다가 재시도한다(지터). */
    private void backOff(int attempt) {
        long maxDelay = Math.min(50, 2L * attempt);
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, maxDelay + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("재시도 중 중단됨", e);
        }
    }
}
