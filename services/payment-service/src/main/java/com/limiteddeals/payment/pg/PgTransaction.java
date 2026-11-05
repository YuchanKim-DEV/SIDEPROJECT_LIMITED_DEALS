package com.limiteddeals.payment.pg;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 모의 PG(결제 대행사) 쪽의 거래 기록. 실제로는 PG 회사의 DB이고, 우리는 조회 API나 정산 파일로만 본다.
 * 여기서는 같은 MySQL에 테이블만 따로 두고, 우리 결제 테이블과 다른 트랜잭션으로 저장해서 "바깥 시스템"을 흉내 낸다.
 */
@Entity
@Table(name = "pg_transactions", uniqueConstraints = @UniqueConstraint(name = "uk_pg_merchant_order_key",
        columnNames = "merchant_order_key"))
@Getter
@NoArgsConstructor
public class PgTransaction {

    public enum Result { APPROVED, DECLINED }

    @Id
    @Column(length = 36)
    private String id;

    /** 가맹점(우리)이 보낸 주문 번호. PG는 이 번호로 같은 결제가 두 번 승인되는 것을 막는다. */
    @Column(name = "merchant_order_key", nullable = false, length = 50)
    private String merchantOrderKey;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Result result;

    @Column(nullable = false)
    private Instant createdAt;

    public PgTransaction(String id, String merchantOrderKey, long amount, Result result) {
        this.id = id;
        this.merchantOrderKey = merchantOrderKey;
        this.amount = amount;
        this.result = result;
        this.createdAt = Instant.now();
    }
}
