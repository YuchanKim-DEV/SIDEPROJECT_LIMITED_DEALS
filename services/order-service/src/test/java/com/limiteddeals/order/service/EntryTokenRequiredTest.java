package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.dto.OrderCreateRequest;
import com.limiteddeals.order.exception.EntryTokenRejectedException;
import com.limiteddeals.order.exception.QueueUnavailableException;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 3단계: 입장권이 필요할 때, 없으면 403이고 대기열 서버가 죽어 있으면 확인 없이 받지 않는다(503). */
@SpringBootTest(properties = {
        "limited-deals.order.entry-token.required=true",
        "limited-deals.order.entry-token.queue-url=http://localhost:1" // 아무것도 없는 포트: 대기열 서버가 죽은 상황
})
class EntryTokenRequiredTest {

    @Autowired OrderFacade orderFacade;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void 입장권_없이_주문하면_거절된다() {
        Long id = productRepository.save(new Product("한정판", 10_000, 10)).getId();

        assertThatThrownBy(() -> orderFacade.placeWithEntryCheck(new OrderCreateRequest(id, 1), StockStrategy.ATOMIC,
                null, "user-1", null))
                .isInstanceOf(EntryTokenRejectedException.class);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void 대기열_서버가_응답하지_않으면_주문을_받지_않는다() {
        Long id = productRepository.save(new Product("한정판", 10_000, 10)).getId();

        assertThatThrownBy(() -> orderFacade.placeWithEntryCheck(new OrderCreateRequest(id, 1), StockStrategy.ATOMIC,
                null, "user-1", "some-token"))
                .isInstanceOf(QueueUnavailableException.class);
        assertThat(orderRepository.count()).isZero();
        assertThat(productRepository.findById(id).orElseThrow().getStock()).isEqualTo(10);
    }
}
