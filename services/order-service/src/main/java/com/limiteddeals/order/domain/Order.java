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

    public Order(Long productId, int quantity) {
        this(productId, quantity, null);
    }

    public Order(Long productId, int quantity, String userId) {
        this.productId = productId;
        this.quantity = quantity;
        this.userId = userId;
    }
}
