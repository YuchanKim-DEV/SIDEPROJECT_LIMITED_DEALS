package com.limiteddeals.payment;

import com.limiteddeals.payment.domain.InboxEventRepository;
import com.limiteddeals.payment.domain.LedgerEntry;
import com.limiteddeals.payment.domain.LedgerEntryRepository;
import com.limiteddeals.payment.domain.PaymentRepository;
import com.limiteddeals.payment.domain.PaymentStatus;
import com.limiteddeals.payment.outbox.OutboxEventRepository;
import com.limiteddeals.payment.pg.PgChaos;
import com.limiteddeals.payment.pg.PgTransaction;
import com.limiteddeals.payment.pg.PgTransactionRepository;
import com.limiteddeals.payment.service.PaymentService;
import com.limiteddeals.payment.service.ReconciliationReport;
import com.limiteddeals.payment.service.ReconciliationService;
import com.limiteddeals.payment.service.SettlementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** 8단계: 결제, 원장, 중복 이벤트, PG 응답 유실과 대사, 정산. */
@SpringBootTest
class PaymentFlowTest {

    @Autowired PaymentService paymentService;
    @Autowired ReconciliationService reconciliationService;
    @Autowired SettlementService settlementService;
    @Autowired PaymentRepository paymentRepository;
    @Autowired LedgerEntryRepository ledgerRepository;
    @Autowired OutboxEventRepository outboxRepository;
    @Autowired InboxEventRepository inboxRepository;
    @Autowired PgTransactionRepository pgRepository;
    @Autowired PgChaos chaos;

    @AfterEach
    void cleanUp() {
        chaos.set(0, 0, 0);
        ledgerRepository.deleteAll();
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
        inboxRepository.deleteAll();
        pgRepository.deleteAll();
    }

    private static String orderPlaced(String eventId, long orderId, long amount) {
        return """
                {"eventId":"%s","orderId":%d,"productId":1,"quantity":1,"amount":%d,"userId":"u1","occurredAt":"%s"}
                """.formatted(eventId, orderId, amount, Instant.now());
    }

    @Test
    void 승인되면_원장에_차변과_대변이_같은_금액으로_남고_완료_이벤트가_나간다() {
        paymentService.handleOrderPlaced(orderPlaced("e-1", 1, 199_000));

        var payment = paymentRepository.findByOrderId(1L).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(ledgerRepository.sumByDirection(LedgerEntry.Direction.DEBIT)).isEqualTo(199_000);
        assertThat(ledgerRepository.sumByDirection(LedgerEntry.Direction.CREDIT)).isEqualTo(199_000);
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo("PaymentCompleted"));
    }

    @Test
    void 같은_주문_이벤트가_세_번_와도_결제와_원장은_한_번이다() {
        String payload = orderPlaced("e-2", 2, 10_000);

        assertThat(paymentService.handleOrderPlaced(payload)).isTrue();
        assertThat(paymentService.handleOrderPlaced(payload)).isFalse();
        assertThat(paymentService.handleOrderPlaced(payload)).isFalse();

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(ledgerRepository.count()).isEqualTo(2);
        assertThat(pgRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    @Test
    void PG가_거절하면_실패_이벤트가_나가고_원장에는_아무것도_없다() {
        chaos.set(1.0, 0, 0);

        paymentService.handleOrderPlaced(orderPlaced("e-3", 3, 10_000));

        assertThat(paymentRepository.findByOrderId(3L).orElseThrow().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(ledgerRepository.count()).isZero();
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo("PaymentFailed"));
    }

    @Test
    void PG_응답을_못_받으면_UNKNOWN으로_두고_대사가_PG_승인을_확인해_끝낸다() {
        chaos.set(0, 1.0, 0); // PG는 승인하지만 응답이 사라진다

        paymentService.handleOrderPlaced(orderPlaced("e-4", 4, 50_000));

        assertThat(paymentRepository.findByOrderId(4L).orElseThrow().getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(pgRepository.findByMerchantOrderKey("order-4").orElseThrow().getResult())
                .isEqualTo(PgTransaction.Result.APPROVED);                // 돈은 이미 승인됐다
        assertThat(outboxRepository.count()).isZero();                  // 주문 쪽에는 아직 아무것도 알리지 않는다

        ReconciliationReport before = reconciliationService.run();

        assertThat(before.resolvedApproved()).isEqualTo(1);
        assertThat(paymentRepository.findByOrderId(4L).orElseThrow().getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(outboxRepository.findAll()).singleElement()
                .satisfies(e -> assertThat(e.getEventType()).isEqualTo("PaymentCompleted"));
        assertThat(before.mismatches()).isZero();

        ReconciliationReport again = reconciliationService.run();          // 다시 돌려도 아무것도 바뀌지 않는다
        assertThat(again.resolvedApproved()).isZero();
        assertThat(ledgerRepository.count()).isEqualTo(2);
    }

    @Test
    void 정산은_원장의_하루_매출에서_수수료를_뺀다() {
        paymentService.handleOrderPlaced(orderPlaced("e-5", 5, 100_000));
        paymentService.handleOrderPlaced(orderPlaced("e-6", 6, 33_333));

        var settlement = settlementService.settle(LocalDate.now(ZoneId.of("Asia/Seoul")));

        assertThat(settlement.count()).isEqualTo(2);
        assertThat(settlement.gross()).isEqualTo(133_333);
        assertThat(settlement.fee()).isEqualTo(3_999);   // 3% = 3,999.99 → 내림
        assertThat(settlement.net()).isEqualTo(129_334);
    }
}
