package com.limiteddeals.order.controller;

import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.service.OrderService;
import com.limiteddeals.order.service.StockStrategy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 주문하기. 재고가 부족하면 409 Conflict.
     * strategy: 재고 차감 방식(naive, atomic). 1단계 비교 실험용이고, 기본값은 atomic.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(@Valid @RequestBody OrderCreateRequest request,
                               @RequestParam(defaultValue = "atomic") String strategy) {
        return orderService.place(request, StockStrategy.valueOf(strategy.toUpperCase()));
    }
}
