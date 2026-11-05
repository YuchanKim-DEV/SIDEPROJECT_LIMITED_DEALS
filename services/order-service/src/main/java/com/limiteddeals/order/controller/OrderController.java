package com.limiteddeals.order.controller;

import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.dto.PlacedOrder;
import com.limiteddeals.order.service.OrderFacade;
import com.limiteddeals.order.service.StockStrategy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;

    /**
     * 주문하기. 재고가 부족하면 409 Conflict.
     * strategy: 재고 차감 방식(naive, atomic, pessimistic, optimistic). 1단계 비교 실험용이고, 기본값은 atomic.
     * Idempotency-Key: 같은 키로 다시 보내면 주문을 새로 만들지 않고 처음 결과를 200과 Idempotent-Replayed: true로 돌려준다.
     * X-User-Id, X-Entry-Token: 대기열 입장권. limited-deals.order.entry-token.required=true일 때 필요하다(없으면 403).
     */
    @PostMapping
    public ResponseEntity<OrderResponse> place(@Valid @RequestBody OrderCreateRequest request,
                                               @RequestParam(defaultValue = "atomic") StockStrategy strategy,
                                               @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                               @RequestHeader(name = "X-User-Id", required = false) String userId,
                                               @RequestHeader(name = "X-Entry-Token", required = false) String entryToken) {
        PlacedOrder placed = orderFacade.placeWithEntryCheck(request, strategy, idempotencyKey, userId, entryToken);
        if (placed.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(placed.response());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(placed.response());
    }
}
