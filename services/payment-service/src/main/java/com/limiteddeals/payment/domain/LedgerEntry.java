package com.limiteddeals.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 원장(ledger) 한 줄. 복식부기: 돈이 움직일 때마다 차변(DEBIT)과 대변(CREDIT)을 같은 금액으로 한 쌍씩 남긴다.
 * 결제 승인 = "PG에게 받을 돈(PG_RECEIVABLE)이 생김(차변)" + "매출(SALES)이 생김(대변)".
 * 그래서 전체 차변 합과 대변 합은 항상 같아야 한다. 다르면 어딘가 기록이 빠졌거나 두 번 들어간 것이다(대사에서 확인).
 *
 * 원장은 고치거나 지우지 않고 추가만 한다(append-only). 환불은 반대 방향 줄을 새로 넣는다.
 * (payment_id, account, direction) 유니크: 같은 결제를 두 번 기록하는 것을 DB가 막는다.
 */
@Entity
@Table(name = "payment_ledger", uniqueConstraints = @UniqueConstraint(name = "uk_ledger_payment_account_direction",
        columnNames = {"payment_id", "account", "direction"}))
@Getter
@NoArgsConstructor
public class LedgerEntry {

    public enum Account { PG_RECEIVABLE, SALES }

    public enum Direction { DEBIT, CREDIT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Direction direction;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private Instant createdAt;

    public LedgerEntry(Long paymentId, Account account, Direction direction, long amount) {
        this.paymentId = paymentId;
        this.account = account;
        this.direction = direction;
        this.amount = amount;
        this.createdAt = Instant.now();
    }
}
