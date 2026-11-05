package com.limiteddeals.payment.service;

import com.limiteddeals.payment.domain.InboxEvent;
import com.limiteddeals.payment.domain.InboxEventRepository;
import com.limiteddeals.payment.domain.Payment;
import com.limiteddeals.payment.domain.PaymentRepository;
import com.limiteddeals.payment.pg.PgClient;
import com.limiteddeals.payment.pg.PgTimeoutException;
import com.limiteddeals.payment.pg.PgTransaction;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 주문 접수 이벤트를 받아 결제한다(Saga의 결제 단계).
 *
 * 1) [트랜잭션 1] Inbox 확인·기록 + 결제(REQUESTED) 저장. 같은 이벤트가 또 오면 여기서 건너뛴다.
 * 2) [트랜잭션 밖] PG 승인 요청. 느린 외부 호출 동안 DB 커넥션과 락을 잡고 있지 않으려고 트랜잭션 밖에서 한다.
 * 3) [트랜잭션 2] 결과 반영(PaymentFinisher): 승인 / 거절 / 응답 없음(UNKNOWN).
 *
 * 1과 3 사이에 서버가 죽으면 결제가 REQUESTED로 남는다. Inbox에는 기록돼서 Kafka가 다시 보내도 건너뛰므로,
 * 대사 배치(ReconciliationService)가 오래된 REQUESTED/UNKNOWN 결제를 PG에 물어 끝낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final InboxEventRepository inboxRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentFinisher finisher;
    private final PgClient pgClient;
    private final TransactionTemplate transactionTemplate;
    private final JsonMapper jsonMapper;
    private final MeterRegistry registry;

    /** @return 새로 결제를 시작했으면 true, 중복 이벤트라 건너뛰었으면 false */
    public boolean handleOrderPlaced(String payload) {
        JsonNode event = jsonMapper.readTree(payload);
        String eventId = event.get("eventId").asString();
        long orderId = event.get("orderId").asLong();
        long amount = event.hasNonNull("amount") ? event.get("amount").asLong() : 0;
        String userId = event.hasNonNull("userId") ? event.get("userId").asString() : null;

        Payment payment = transactionTemplate.execute(status -> {
            if (inboxRepository.existsByEventId(eventId)) {
                return null;
            }
            inboxRepository.save(new InboxEvent(eventId, "OrderPlaced"));
            return paymentRepository.save(new Payment(orderId, userId, amount));
        });
        if (payment == null) {
            registry.counter("payment.duplicates").increment();
            log.info("중복 이벤트 건너뜀: eventId={}", eventId);
            return false;
        }
        if (amount <= 0) {
            finisher.fail(payment, "결제 금액 없음", "consumer");
            return true;
        }
        pay(payment);
        return true;
    }

    private void pay(Payment payment) {
        try {
            PgTransaction result = pgClient.approve(payment.merchantOrderKey(), payment.getAmount());
            if (result.getResult() == PgTransaction.Result.APPROVED) {
                finisher.approve(payment, result.getId(), "consumer");
            } else {
                finisher.fail(payment, "PG 거절", "consumer");
            }
        } catch (PgTimeoutException e) {
            // 실패로 처리하면 안 된다. PG는 승인했을 수 있다(돈은 빠졌는데 주문은 취소되는 최악의 경우).
            log.warn("PG 응답 없음, 대사에서 확인: orderId={}", payment.getOrderId());
            finisher.markUnknown(payment);
        }
    }
}
