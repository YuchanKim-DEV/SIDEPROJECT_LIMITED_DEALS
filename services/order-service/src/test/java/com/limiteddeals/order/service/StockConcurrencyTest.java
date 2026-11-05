package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.exception.OutOfStockException;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재고 50개에 200명이 동시에 1개씩 주문한다. 막는 방식(atomic, pessimistic, optimistic)은 항상 50건만 팔려야 한다.
 * H2로 돌리는 빠른 확인용이다. 실제 MySQL 수치는 scripts/load/oversell.sh와 docs/experiments/01에 있다.
 */
@SpringBootTest
class StockConcurrencyTest {

    private static final int STOCK = 50;
    private static final int BUYERS = 200;

    @Autowired OrderFacade orderFacade;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @ParameterizedTest
    @EnumSource(value = StockStrategy.class, names = {"ATOMIC", "PESSIMISTIC", "OPTIMISTIC"})
    void 동시에_몰려도_재고만큼만_팔린다(StockStrategy strategy) throws Exception {
        Long id = productRepository.save(new Product("동시성 테스트", 10_000, STOCK)).getId();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(32);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < BUYERS; i++) {
            futures.add(pool.submit(() -> {
                ready.await(); // 모두 준비시킨 뒤 한꺼번에 출발
                try {
                    orderFacade.place(new OrderCreateRequest(id, 1), strategy);
                    success.incrementAndGet();
                } catch (OutOfStockException e) {
                    soldOut.incrementAndGet();
                }
                return null;
            }));
        }
        ready.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS); // 예상하지 못한 예외가 있으면 여기서 실패한다
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(STOCK);
        assertThat(soldOut.get()).isEqualTo(BUYERS - STOCK);
        assertThat(productRepository.findById(id).orElseThrow().getStock()).isZero();
        assertThat(orderRepository.count()).isEqualTo(STOCK);
    }
}
