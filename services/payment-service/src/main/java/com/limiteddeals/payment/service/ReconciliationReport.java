package com.limiteddeals.payment.service;

import java.time.Instant;

/**
 * 대사 결과.
 * resolvedApproved / resolvedFailed: 끝나지 않았던 결제를 PG에 물어 이번에 끝낸 수.
 * pgApprovedButNotOurs: PG는 승인했는데 우리는 승인 상태가 아닌 결제(돈은 받았는데 주문이 안 된 상태).
 * oursApprovedButNotPg: 우리는 승인인데 PG에는 승인 기록이 없는 결제(돈을 못 받았는데 상품이 나가는 상태).
 * amountMismatches: 양쪽 금액이 다른 결제.
 * ledgerDebit / ledgerCredit: 원장 차변 합, 대변 합(같아야 한다). approvedWithoutLedger: 승인됐는데 원장 기록이 없는 결제.
 */
public record ReconciliationReport(Instant ranAt, int resolvedApproved, int resolvedFailed, int stillOpen,
                                   int pgApprovedButNotOurs, int oursApprovedButNotPg, int amountMismatches,
                                   long ledgerDebit, long ledgerCredit, long approvedWithoutLedger, long approvedTotal) {

    public int mismatches() {
        return pgApprovedButNotOurs + oursApprovedButNotPg + amountMismatches + (ledgerDebit == ledgerCredit ? 0 : 1)
                + (int) approvedWithoutLedger + (ledgerCredit == approvedTotal ? 0 : 1);
    }
}
