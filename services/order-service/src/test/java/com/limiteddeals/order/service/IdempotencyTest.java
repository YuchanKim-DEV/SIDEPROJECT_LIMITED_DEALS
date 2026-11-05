package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.PlacedOrder;
import com.limiteddeals.order.exception.IdempotencyInProgressException;
import com.limiteddeals.order.exception.IdempotencyKeyMismatchException;
import com.limiteddeals.order.exception.OutOfStockException;
import com.limiteddeals.order.repository.IdempotencyRecordRepository;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 2단계: 같은 Idempotency-Key로 여러 번 보내도 주문은 한 번만 생긴다. */
@SpringBootTest
class IdempotencyTest {

    @Autowired OrderFacade orderFacade;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired IdempotencyRecordRepository idempotencyRecordRepository;

    @AfterEach
    void cleanUp() {
        idempotencyRecordRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void 같은_키로_두_번_보내면_두_번째는_처음_결과를_돌려준다() {
        Long id = productRepository.save(new Product("한정판", 10_000, 10)).getId();
        OrderCreateRequest request = new OrderCreateRequest(id, 1);

        PlacedOrder first = orderFacade.place(request, StockStrategy.ATOMIC, "key-1");
        PlacedOrder second = orderFacade.place(request, StockStrategy.ATOMIC, "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.response()).isEqualTo(first.response());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(id).orElseThrow().getStock()).isEqualTo(9);
    }

    @Test
    void 같은_키로_다른_내용을_보내면_거절한다() {
        Long id = productRepository.save(new Product("한정판", 10_000, 10)).getId();
        orderFacade.place(new OrderCreateRequest(id, 1), StockStrategy.ATOMIC, "key-2");

        assertThatThrownBy(() -> orderFacade.place(new OrderCreateRequest(id, 3), StockStrategy.ATOMIC, "key-2"))
                .isInstanceOf(IdempotencyKeyMismatchException.class);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void 실패한_요청의_키는_남지_않아서_같은_키로_다시_시도할_수_있다() {
        Long id = productRepository.save(new Product("한정판", 10_000, 0)).getId();

        assertThatThrownBy(() -> orderFacade.place(new OrderCreateRequest(id, 1), StockStrategy.ATOMIC, "key-3"))
                .isInstanceOf(OutOfStockException.class);

        assertThat(idempotencyRecordRepository.findByIdempotencyKey("key-3")).isEmpty();
    }

    @Test
    void 같은_키가_동시에_20번_와도_주문은_한_건이다() throws Exception {
        Long id = productRepository.save(new Product("한정판", 10_000, 10)).getId();
        OrderCreateRequest request = new OrderCreateRequest(id, 1);
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            futures.add(pool.submit(() -> {
                ready.await();
                try {
                    return orderFacade.place(request, StockStrategy.ATOMIC, "key-4").response().orderId();
                } catch (IdempotencyInProgressException inProgress) {
                    return null; // 첫 요청이 처리 중일 때 온 것. 다시 보내면 결과를 받는다
                }
            }));
        }
        ready.countDown();
        Set<Long> orderIds = ConcurrentHashMap.newKeySet();
        for (Future<Long> f : futures) {
            Long orderId = f.get(30, TimeUnit.SECONDS);
            if (orderId != null) orderIds.add(orderId);
        }
        pool.shutdown();

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderIds).hasSize(1); // 결과를 받은 요청은 모두 같은 주문 번호
        assertThat(productRepository.findById(id).orElseThrow().getStock()).isEqualTo(9);
    }
}
