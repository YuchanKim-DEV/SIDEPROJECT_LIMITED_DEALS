package com.limiteddeals.order.service;

import com.limiteddeals.order.domain.Product;
import com.limiteddeals.order.repository.OrderRepository;
import com.limiteddeals.order.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 1-3 학습용: 트랜잭션 상자가 있을 때와 없을 때를 비교한다.
 * 상황: 재고를 줄인 "다음에" 주문 저장이 실패한다.
 */
@SpringBootTest
class TransactionDemoTest {

    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired TransactionTemplate transactionTemplate; // 코드 블록을 트랜잭션 상자에 넣어주는 도구

    @AfterEach
    void cleanUp() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void 상자가_없으면_재고만_줄고_주문은_없는_상태가_남는다() {
        Long id = productRepository.save(new Product("한정판 후드", 89_000, 10)).getId();

        assertThatThrownBy(() -> {
            Product product = productRepository.findById(id).orElseThrow();
            product.decreaseStock(1);
            productRepository.save(product);              // ① 재고 10 → 9 저장 (바로 확정됨)
            throw new IllegalStateException("주문 저장 실패"); // ② 주문 저장 단계에서 실패
        }).isInstanceOf(IllegalStateException.class);

        assertThat(productRepository.findById(id).orElseThrow().getStock()).isEqualTo(9); // 재고는 줄었는데
        assertThat(orderRepository.count()).isZero();                                     // 주문은 없다 (데이터가 어긋남)
    }

    @Test
    void 상자_안에서_실패하면_재고도_원래대로_돌아간다() {
        Long id = productRepository.save(new Product("한정판 후드", 89_000, 10)).getId();

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findById(id).orElseThrow();
            product.decreaseStock(1);                      // ① 재고 10 → 9 (아직 확정 전)
            throw new IllegalStateException("주문 저장 실패"); // ② 실패 → 상자 전체를 되돌림(롤백)
        })).isInstanceOf(IllegalStateException.class);

        assertThat(productRepository.findById(id).orElseThrow().getStock()).isEqualTo(10); // 재고는 그대로
        assertThat(orderRepository.count()).isZero();
    }
}
