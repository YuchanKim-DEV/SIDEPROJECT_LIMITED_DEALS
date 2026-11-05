package com.limiteddeals.order.controller;

import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderDetailResponse;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.dto.PlacedOrder;
import com.limiteddeals.order.exception.OrderNotFoundException;
import com.limiteddeals.order.service.OrderFacade;
import com.limiteddeals.order.service.OrderService;
import com.limiteddeals.order.service.StockStrategy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderFacade orderFacade;
    private final OrderService orderService;

    /**
     * 주문 조회. 결제가 끝나면 status가 PAID, 결제가 실패하면 CANCELLED가 된다.
     * 내 주문만 볼 수 있다(관리자는 모두). X-User-Id, X-User-Role은 게이트웨이가 토큰을 확인하고 붙인 값이다.
     * 남의 주문이면 403이 아니라 404로 답한다. 403이면 "그 번호의 주문이 있다"는 사실을 알려 주게 된다.
     */
    @GetMapping("/{orderId}")
    public OrderDetailResponse find(@PathVariable Long orderId,
                                    @RequestHeader(name = "X-User-Id", required = false) String userId,
                                    @RequestHeader(name = "X-User-Role", required = false) String role) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다");
        }
        OrderDetailResponse order = orderService.findOrder(orderId);
        if (!"ADMIN".equals(role) && !userId.equals(order.userId())) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

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
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 100)) {
            // DB 칸(100자)보다 긴 키는 저장할 때 500이 난다. 들어오는 입구에서 400으로 막는다
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key는 1~100자여야 합니다");
        }
        PlacedOrder placed = orderFacade.placeWithEntryCheck(request, strategy, idempotencyKey, userId, entryToken);
        if (placed.replayed()) {
            return ResponseEntity.ok().header("Idempotent-Replayed", "true").body(placed.response());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(placed.response());
    }
}
