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

    public Order(Long productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }
}
