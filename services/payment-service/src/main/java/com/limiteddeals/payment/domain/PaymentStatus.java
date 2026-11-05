package com.limiteddeals.payment.domain;

/**
 * 결제 상태.
 * REQUESTED: 결제를 시작했다(PG에 요청하기 직전 또는 요청 중).
 * UNKNOWN:   PG에 요청했지만 답을 못 받았다(시간 초과). 승인됐을 수도, 안 됐을 수도 있다. 대사(reconciliation)가 PG에 물어 정한다.
 * APPROVED, FAILED: 끝난 상태. 한 번 정해지면 바뀌지 않는다.
 */
public enum PaymentStatus {
    REQUESTED, UNKNOWN, APPROVED, FAILED;

    public boolean isFinal() {
        return this == APPROVED || this == FAILED;
    }
}
