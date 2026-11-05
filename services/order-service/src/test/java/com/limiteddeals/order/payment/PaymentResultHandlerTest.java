package com.limiteddeals.order.payment;

import com.limiteddeals.order.domain.OrderStatus;
import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.outbox.OutboxEventRepository;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import com.limiteddeals.order.service.OrderService;
import com.limiteddeals.order.service.StockStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 8단계 Saga: 결제 결과에 따라 주문이 확정되거나 취소되고, 취소되면 재고가 한 번만 되돌아온다. */
@SpringBootTest
class PaymentResultHandlerTest {

    @Autowired PaymentResultHandler handler;
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
    void 주문하면_결제_대기로_시작하고_금액이_정해진다() {
        Long productId = productRepository.save(new Product("한정판", 10_000, 10)).getId();
        Long orderId = orderService.place(new OrderCreateRequest(productId, 3), StockStrategy.ATOMIC, "u1").orderId();

        var order = orderService.findOrder(orderId);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order.amount()).isEqualTo(30_000);
        assertThat(outboxEventRepository.findAll().getFirst().getPayload()).contains("\"amount\":30000");
    }

    @Test
    void 결제가_성공하면_주문이_확정된다() {
        Long productId = productRepository.save(new Product("한정판", 10_000, 10)).getId();
        Long orderId = orderService.place(new OrderCreateRequest(productId, 1), StockStrategy.ATOMIC, "u1").orderId();

        assertThat(handler.handle(result("PaymentCompleted", orderId))).isTrue();

        assertThat(orderService.findOrder(orderId).status()).isEqualTo(OrderStatus.PAID);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(9);
    }

    @Test
    void 결제가_실패하면_주문이_취소되고_재고가_한_번만_돌아온다() {
        Long productId = productRepository.save(new Product("한정판", 10_000, 10)).getId();
        Long orderId = orderService.place(new OrderCreateRequest(productId, 2), StockStrategy.ATOMIC, "u1").orderId();
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(8);

        assertThat(handler.handle(result("PaymentFailed", orderId))).isTrue();
        assertThat(handler.handle(result("PaymentFailed", orderId))).isFalse();     // 같은 결과가 또 와도
        assertThat(handler.handle(result("PaymentCompleted", orderId))).isFalse();  // 뒤늦게 다른 결과가 와도

        assertThat(orderService.findOrder(orderId).status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(10); // 12가 아니다
    }

    private String result(String type, Long orderId) {
        return "{\"eventId\":\"e-" + type + "-" + orderId + "\",\"type\":\"" + type + "\",\"orderId\":" + orderId + "}";
    }
}
