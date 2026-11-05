package com.limiteddeals.order.service;

import com.limiteddeals.order.client.EntryTokenVerifier;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.dto.PlacedOrder;
import com.limiteddeals.order.exception.OptimisticConflictException;
import com.limiteddeals.order.exception.TooManyConflictsException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 컨트롤러가 부르는 주문 입구. 트랜잭션 바깥에서 해야 하는 일(재시도, 멱등 키 중복 처리)을 맡는다.
 * 재시도와 중복 처리는 반드시 트랜잭션 "바깥"에서 한다. 실패한 트랜잭션은 되돌려야 하므로, 매번 새 트랜잭션으로 다시 시작한다.
 */
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderService orderService;
    private final EntryTokenVerifier entryTokenVerifier;

    @Value("${limited-deals.order.optimistic.max-attempts:50}")
    private int maxAttempts;

    /** true면 대기열 입장권이 있어야 주문할 수 있다. 1·2단계 실험(대기열 없이 직접 주문)을 위해 기본은 false. */
    @Value("${limited-deals.order.entry-token.required:false}")
    private boolean entryTokenRequired;

    /**
     * 오픈런 전체 흐름의 주문 입구: 입장권 확인(트랜잭션 밖, 네트워크 호출) → 멱등 주문.
     * 입장권 확인을 트랜잭션 밖에서 하는 이유: 네트워크를 기다리는 동안 DB 커넥션을 붙잡지 않기 위해서다.
     */
    public PlacedOrder placeWithEntryCheck(OrderCreateRequest request, StockStrategy strategy, String idempotencyKey,
                                           String userId, String entryToken) {
        if (entryTokenRequired) {
            entryTokenVerifier.verify(request.productId(), userId, entryToken);
        }
        return place(request, strategy, idempotencyKey);
    }

    /** 멱등 키 없이 주문한다. */
    public OrderResponse place(OrderCreateRequest request, StockStrategy strategy) {
        return withOptimisticRetry(request, strategy, () -> orderService.place(request, strategy));
    }

    /**
     * 멱등 키와 함께 주문한다(키가 없으면 그냥 주문).
     * 같은 키로 다시 오면 주문을 새로 만들지 않고 처음 결과를 replayed=true로 돌려준다.
     */
    public PlacedOrder place(OrderCreateRequest request, StockStrategy strategy, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new PlacedOrder(place(request, strategy), false);
        }
        String hash = requestHash(request);
        try {
            OrderResponse created = withOptimisticRetry(request, strategy,
                    () -> orderService.placeWithIdempotencyKey(idempotencyKey, hash, request, strategy));
            return new PlacedOrder(created, false);
        } catch (DataIntegrityViolationException duplicateKey) {
            return new PlacedOrder(orderService.findReplay(idempotencyKey, hash), true);
        }
    }

    private OrderResponse withOptimisticRetry(OrderCreateRequest request, StockStrategy strategy,
                                              Supplier<OrderResponse> attempt) {
        if (strategy != StockStrategy.OPTIMISTIC) {
            return attempt.get();
        }
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                return attempt.get(); // 시도마다 새 트랜잭션
            } catch (OptimisticConflictException conflict) {
                backOff(i);
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

    /** 같은 키에 다른 내용을 보냈는지 알아보기 위한 요청 지문(SHA-256). */
    private static String requestHash(OrderCreateRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((request.productId() + ":" + request.quantity()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
