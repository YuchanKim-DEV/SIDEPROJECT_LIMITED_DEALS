package com.limiteddeals.order.domain;

import com.limiteddeals.order.dto.OrderResponse;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 멱등 키 기록. 같은 Idempotency-Key로 다시 오면 주문을 새로 만들지 않고 처음 결과를 돌려준다.
 * idempotency_key의 유니크 제약이 "같은 키는 한 번만"을 DB 수준에서 보장한다.
 * 동시에 같은 키가 두 번 오면, 두 번째 INSERT는 첫 번째 트랜잭션이 끝날 때까지 기다렸다가 중복 오류가 난다.
 */
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor
public class IdempotencyRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    /** 같은 키에 다른 내용(상품, 수량)을 보내는 실수를 잡기 위한 요청 지문. */
    @Column(nullable = false, length = 64)
    private String requestHash;

    private Long orderId;
    private Long productId;
    private Integer quantity;
    private Integer remainingStock;

    @Column(nullable = false)
    private Instant createdAt;

    public IdempotencyRecord(String idempotencyKey, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.createdAt = Instant.now();
    }

    public void complete(OrderResponse response) {
        this.orderId = response.orderId();
        this.productId = response.productId();
        this.quantity = response.quantity();
        this.remainingStock = response.remainingStock();
    }

    public boolean isCompleted() {
        return orderId != null;
    }

    public OrderResponse toResponse() {
        return new OrderResponse(orderId, productId, quantity, remainingStock);
    }
}
