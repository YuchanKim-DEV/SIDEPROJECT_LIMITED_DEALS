package com.limiteddeals.payment.pg;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 모의 PG 서버(바깥 시스템). 우리 트랜잭션과 상관없이 자기 기록을 바로 확정한다(REQUIRES_NEW).
 * 그래서 우리 쪽이 응답을 못 받거나 실패해도 PG에는 승인이 남을 수 있다. 실제 PG와 같은 상황이다.
 */
@Component
@RequiredArgsConstructor
public class MockPgServer {

    private final PgTransactionRepository repository;
    private final PgChaos chaos;

    /** 승인 요청. 같은 주문 번호로 다시 오면 새로 승인하지 않고 처음 결과를 돌려준다(PG 쪽 멱등성). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgTransaction approve(String merchantOrderKey, long amount) {
        return repository.findByMerchantOrderKey(merchantOrderKey).orElseGet(() -> {
            boolean declined = ThreadLocalRandom.current().nextDouble() < chaos.declineRate();
            return repository.save(new PgTransaction(UUID.randomUUID().toString(), merchantOrderKey, amount,
                    declined ? PgTransaction.Result.DECLINED : PgTransaction.Result.APPROVED));
        });
    }

    /** 거래 조회 API. 대사할 때 "이 주문, 너희 쪽에서는 어떻게 됐어?"를 묻는다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Optional<PgTransaction> inquire(String merchantOrderKey) {
        return repository.findByMerchantOrderKey(merchantOrderKey);
    }

    /** 정산 파일 대신: PG가 승인했다고 기록한 거래 전체. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<PgTransaction> approvedTransactions() {
        return repository.findByResult(PgTransaction.Result.APPROVED);
    }
}
