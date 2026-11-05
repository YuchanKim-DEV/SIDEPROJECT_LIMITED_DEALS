package com.limiteddeals.payment.pg;

/** PG 응답을 받지 못했다. 승인됐는지 알 수 없다(실패가 아니다). */
public class PgTimeoutException extends RuntimeException {

    public PgTimeoutException(String merchantOrderKey) {
        super("PG 응답 없음(시간 초과): " + merchantOrderKey);
    }
}
