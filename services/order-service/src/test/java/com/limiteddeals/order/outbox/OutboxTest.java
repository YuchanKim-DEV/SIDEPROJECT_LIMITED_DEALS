package com.limiteddeals.order.outbox;

import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.exception.OutOfStockException;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import com.limiteddeals.order.service.OrderService;
import com.limiteddeals.order.service.StockStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 4단계: 주문과 이벤트가 같은 트랜잭션으로 저장된다. */
@SpringBootTest
class OutboxTest {

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
    void 주문하면_보낼_이벤트가_함께_저장된다() {
        Long id = productRepository.save(new Product("한정판", 10_000, 10)).getId();

        var response = orderService.place(new OrderCreateRequest(id, 2), StockStrategy.ATOMIC, "user-7");

        var events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.Status.PENDING);
        assertThat(event.getEventType()).isEqualTo("OrderPlaced");
        assertThat(event.getMessageKey()).isEqualTo(String.valueOf(id));
        assertThat(event.getPayload())
                .contains("\"orderId\":" + response.orderId())
                .contains("\"eventId\":\"" + event.getEventId() + "\"")
                .contains("\"userId\":\"user-7\"");
    }

    @Test
    void 주문이_실패하면_이벤트도_남지_않는다() {
        Long id = productRepository.save(new Product("한정판", 10_000, 0)).getId();

        assertThatThrownBy(() -> orderService.place(new OrderCreateRequest(id, 1), StockStrategy.ATOMIC, null))
                .isInstanceOf(OutOfStockException.class);

        assertThat(outboxEventRepository.count()).isZero();
    }
}
