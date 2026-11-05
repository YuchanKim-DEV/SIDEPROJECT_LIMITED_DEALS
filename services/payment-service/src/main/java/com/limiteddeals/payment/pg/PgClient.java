package com.limiteddeals.payment.pg;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 우리 쪽 PG 호출 코드. 실제라면 HTTP 클라이언트이고, 여기서는 모의 PG 서버를 부른다.
 * timeoutRate만큼은 PG가 처리를 끝낸 "뒤에" 응답이 사라진 것처럼 PgTimeoutException을 던진다.
 */
@Component
@RequiredArgsConstructor
public class PgClient {

    private final MockPgServer server;
    private final PgChaos chaos;

    public PgTransaction approve(String merchantOrderKey, long amount) {
        sleep(chaos.latencyMs());
        PgTransaction transaction = server.approve(merchantOrderKey, amount); // PG에는 이미 기록됨
        if (ThreadLocalRandom.current().nextDouble() < chaos.timeoutRate()) {
            throw new PgTimeoutException(merchantOrderKey);
        }
        return transaction;
    }

    public Optional<PgTransaction> inquire(String merchantOrderKey) {
        return server.inquire(merchantOrderKey);
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
