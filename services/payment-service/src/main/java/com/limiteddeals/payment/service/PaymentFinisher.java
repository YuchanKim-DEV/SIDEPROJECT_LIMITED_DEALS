package com.limiteddeals.payment.service;

import com.limiteddeals.payment.domain.LedgerEntry;
import com.limiteddeals.payment.domain.LedgerEntry.Account;
import com.limiteddeals.payment.domain.LedgerEntry.Direction;
import com.limiteddeals.payment.domain.LedgerEntryRepository;
import com.limiteddeals.payment.domain.Payment;
import com.limiteddeals.payment.domain.PaymentRepository;
import com.limiteddeals.payment.domain.PaymentStatus;
import com.limiteddeals.payment.outbox.OutboxEvent;
import com.limiteddeals.payment.outbox.OutboxEventRepository;
import com.limiteddeals.payment.outbox.PaymentResultEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

/**
 * 결제를 끝낸다(승인 또는 실패). 컨슈머와 대사 배치가 함께 쓴다.
 * 한 트랜잭션: 상태 전이(끝나지 않았을 때만) + 원장 기록(승인일 때) + 결과 이벤트를 Outbox에 저장.
 * 상태 전이에 진 쪽(이미 끝난 결제)은 아무것도 하지 않는다. 그래서 원장이 두 번 기록되거나 이벤트가 두 번 나가지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PaymentFinisher {

    private static final EnumSet<PaymentStatus> OPEN = EnumSet.of(PaymentStatus.REQUESTED, PaymentStatus.UNKNOWN);

    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final OutboxEventRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final MeterRegistry registry;

    /** @return 이번 호출이 결제를 끝냈으면 true */
    @Transactional
    public boolean approve(Payment payment, String pgTransactionId, String by) {
        if (paymentRepository.transition(payment.getId(), OPEN, PaymentStatus.APPROVED, pgTransactionId, null, Instant.now()) == 0) {
            return false;
        }
        ledgerRepository.save(new LedgerEntry(payment.getId(), Account.PG_RECEIVABLE, Direction.DEBIT, payment.getAmount()));
        ledgerRepository.save(new LedgerEntry(payment.getId(), Account.SALES, Direction.CREDIT, payment.getAmount()));
        publish(payment, PaymentResultEvent.COMPLETED, null);
        registry.counter("payment.finished", "result", "approved", "by", by).increment();
        return true;
    }

    @Transactional
    public boolean fail(Payment payment, String reason, String by) {
        if (paymentRepository.transition(payment.getId(), OPEN, PaymentStatus.FAILED, null, reason, Instant.now()) == 0) {
            return false;
        }
        publish(payment, PaymentResultEvent.FAILED, reason);
        registry.counter("payment.finished", "result", "failed", "by", by).increment();
        return true;
    }

    /** PG 응답을 못 받았다. 끝내지 않고 UNKNOWN으로 남겨, 대사가 PG에 물어서 정하게 한다. */
    @Transactional
    public void markUnknown(Payment payment) {
        paymentRepository.transition(payment.getId(), EnumSet.of(PaymentStatus.REQUESTED), PaymentStatus.UNKNOWN, null,
                "PG 응답 없음", Instant.now());
        registry.counter("payment.unknown").increment();
    }

    private void publish(Payment payment, String type, String reason) {
        String eventId = UUID.randomUUID().toString();
        PaymentResultEvent event = new PaymentResultEvent(eventId, type, payment.getId(), payment.getOrderId(),
                payment.getAmount(), reason, Instant.now());
        outboxRepository.save(new OutboxEvent(eventId, type, String.valueOf(payment.getOrderId()),
                jsonMapper.writeValueAsString(event)));
    }
}
