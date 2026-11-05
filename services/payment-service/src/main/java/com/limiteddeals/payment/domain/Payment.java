package com.limiteddeals.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 주문 한 건의 결제. 주문 하나에 결제는 하나다(order_id 유니크). */
@Entity
@Table(name = "payments", uniqueConstraints = @UniqueConstraint(name = "uk_payment_order_id", columnNames = "order_id"),
        indexes = @Index(name = "idx_payment_status_updated", columnList = "status, updated_at"))
@Getter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(length = 50)
    private String userId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** PG가 붙인 거래 번호. 승인됐을 때만 있다. */
    @Column(length = 36)
    private String pgTransactionId;

    @Column(length = 200)
    private String failureReason;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Payment(Long orderId, String userId, long amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.status = PaymentStatus.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** PG가 결제를 대신 받아 주는 곳의 주문 번호. PG는 이 번호로 중복 결제를 막는다. */
    public String merchantOrderKey() {
        return "order-" + orderId;
    }
}
