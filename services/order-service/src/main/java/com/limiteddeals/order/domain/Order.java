package com.limiteddeals.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 주문 한 건. ORDER는 SQL 예약어라서 테이블 이름은 orders로 한다. */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    /** 주문한 사용자. 대기열·로그인을 거친 주문에만 있다. */
    @Column(length = 50)
    private String userId;

    /** 결제할 금액(상품 가격 × 수량). 7단계 이전 주문에는 없다. */
    private Long amount;

    /** 7단계 이전 주문에는 없다(결제 없이 바로 확정되던 때). */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus status;

    public Order(Long productId, int quantity) {
        this(productId, quantity, null);
    }

    public Order(Long productId, int quantity, String userId) {
        this(productId, quantity, userId, null);
    }

    public Order(Long productId, int quantity, String userId, Long amount) {
        this.productId = productId;
        this.quantity = quantity;
        this.userId = userId;
        this.amount = amount;
        this.status = OrderStatus.PENDING_PAYMENT;
    }
}
