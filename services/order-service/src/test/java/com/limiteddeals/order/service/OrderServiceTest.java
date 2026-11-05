package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.dto.OrderResponse;
import com.limiteddeals.order.exception.OutOfStockException;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import com.limiteddeals.order.outbox.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderServiceTest {

    @Autowired OrderService orderService;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired OutboxEventRepository outboxEventRepository;

    @AfterEach
    void cleanUp() {
        outboxEventRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void 주문하면_재고가_줄고_주문이_저장된다() {
        Long id = productRepository.save(new Product("한정판 스니커즈", 199_000, 100)).getId();

        OrderResponse response = orderService.place(new OrderCreateRequest(id, 2));

        assertThat(response.remainingStock()).isEqualTo(98);
        assertThat(productRepository.findById(id).orElseThrow().getStock()).isEqualTo(98);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void 재고보다_많이_사면_거절되고_아무것도_바뀌지_않는다() {
        Long id = productRepository.save(new Product("한정판 후드", 89_000, 1)).getId();

        assertThatThrownBy(() -> orderService.place(new OrderCreateRequest(id, 2)))
                .isInstanceOf(OutOfStockException.class);

        assertThat(productRepository.findById(id).orElseThrow().getStock()).isEqualTo(1);
        assertThat(orderRepository.count()).isZero();
    }
}
