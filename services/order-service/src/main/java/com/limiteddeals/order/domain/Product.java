package com.limiteddeals.order.domain;

import com.limiteddeals.order.exception.OutOfStockException;
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

    /**
     * 낙관적 락용 버전 번호. 재고를 바꿀 때마다 1씩 오른다.
     * JPA의 @Version을 쓰지 않고 직접 비교한다(naive 방식은 버전을 보지 않아야 비교 실험이 된다).
     */
    @Column(nullable = false)
    private long version;

    public Product(String name, long price, int stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    /** 재고를 줄인다. 남은 재고보다 많이 사려고 하면 거절한다. */
    public void decreaseStock(int quantity) {
        if (stock < quantity) {
            throw new OutOfStockException(id, stock, quantity);
        }
        stock -= quantity;
    }
}
