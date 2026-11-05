package com.limiteddeals.payment.pg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 실험용 PG 장애 설정. 실행 중에 바꿀 수 있다(POST /api/payments/admin/pg, CHAOS_ENABLED=true일 때만).
 * declineRate: 카드 한도 초과처럼 PG가 거절하는 비율.
 * timeoutRate: PG는 승인했는데 응답이 우리에게 오지 않는 비율(네트워크 끊김, 시간 초과). 가장 위험한 경우다.
 * latencyMs:   PG 응답 시간.
 */
@Component
public class PgChaos {

    private volatile double declineRate;
    private volatile double timeoutRate;
    private volatile long latencyMs;

    public PgChaos(@Value("${limited-deals.payment.pg.decline-rate:0}") double declineRate,
                   @Value("${limited-deals.payment.pg.timeout-rate:0}") double timeoutRate,
                   @Value("${limited-deals.payment.pg.latency-ms:20}") long latencyMs) {
        set(declineRate, timeoutRate, latencyMs);
    }

    public void set(double declineRate, double timeoutRate, long latencyMs) {
        this.declineRate = declineRate;
        this.timeoutRate = timeoutRate;
        this.latencyMs = latencyMs;
    }

    public double declineRate() { return declineRate; }

    public double timeoutRate() { return timeoutRate; }

    public long latencyMs() { return latencyMs; }
}
