package com.limiteddeals.order.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 오픈런으로 파는 한정판 상품.
 * stock(재고 수량)이 1단계 동시성 실험의 대상이다.
 */
@Entity
@Getter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private int stock;

    public Product(String name, long price, int stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }
}
