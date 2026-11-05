package com.limiteddeals.payment.service;

import com.limiteddeals.payment.domain.LedgerEntry;
import com.limiteddeals.payment.domain.LedgerEntryRepository;
import com.limiteddeals.payment.domain.Payment;
import com.limiteddeals.payment.domain.PaymentRepository;
import com.limiteddeals.payment.domain.PaymentStatus;
import com.limiteddeals.payment.pg.PgClient;
import com.limiteddeals.payment.pg.PgTransaction;
import com.limiteddeals.payment.pg.MockPgServer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 대사(reconciliation): 우리 기록과 PG 기록, 원장을 서로 맞춰 본다. 주기적으로(기본 30초) 돈다.
 *
 * 1) 오래 끝나지 않은 결제(REQUESTED, UNKNOWN)를 PG에 조회해서 끝낸다.
 *    PG에 승인 기록이 있으면 승인, 거절 기록이 있으면 실패, 기록이 없으면 PG가 받지 못한 것이므로 실패.
 * 2) PG 승인 목록(정산 파일 대신)과 우리 승인 결제를 비교한다.
 * 3) 원장 차변 합 = 대변 합 = 승인 결제 금액 합인지 확인한다.
 * 2, 3에서 어긋난 것은 자동으로 고치지 않고 수치와 ERROR 로그로 알린다(사람이 원인을 봐야 하는 경우).
 */
@Slf4j
@Service
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final PaymentFinisher finisher;
    private final PgClient pgClient;
    private final MockPgServer pgServer;
    private final Duration openTimeout;
    private final AtomicReference<ReconciliationReport> last = new AtomicReference<>();

    public ReconciliationService(PaymentRepository paymentRepository, LedgerEntryRepository ledgerRepository,
                                 PaymentFinisher finisher, PgClient pgClient, MockPgServer pgServer, MeterRegistry registry,
                                 @Value("${limited-deals.payment.reconcile-open-after-seconds:30}") long openAfterSeconds) {
        this.paymentRepository = paymentRepository;
        this.ledgerRepository = ledgerRepository;
        this.finisher = finisher;
        this.pgClient = pgClient;
        this.pgServer = pgServer;
        this.openTimeout = Duration.ofSeconds(openAfterSeconds);
        Gauge.builder("payment.reconciliation.mismatches", () -> last.get() == null ? 0 : last.get().mismatches())
                .description("마지막 대사에서 어긋난 건수").register(registry);
        Gauge.builder("payment.open", () -> paymentRepository.countByStatus(PaymentStatus.REQUESTED)
                        + paymentRepository.countByStatus(PaymentStatus.UNKNOWN))
                .description("끝나지 않은 결제 수(REQUESTED + UNKNOWN)").register(registry);
    }

    @Scheduled(fixedDelayString = "${limited-deals.payment.reconcile-interval-ms:30000}",
            initialDelayString = "${limited-deals.payment.reconcile-interval-ms:30000}")
    public void scheduled() {
        run();
    }

    public ReconciliationReport run() {
        int resolvedApproved = 0;
        int resolvedFailed = 0;
        List<Payment> open = paymentRepository.findByStatusInAndUpdatedAtBefore(
                EnumSet.of(PaymentStatus.REQUESTED, PaymentStatus.UNKNOWN), Instant.now().minus(openTimeout));
        for (Payment payment : open) {
            Optional<PgTransaction> pg = pgClient.inquire(payment.merchantOrderKey());
            if (pg.isPresent() && pg.get().getResult() == PgTransaction.Result.APPROVED) {
                if (finisher.approve(payment, pg.get().getId(), "reconciliation")) resolvedApproved++;
            } else {
                String reason = pg.isPresent() ? "PG 거절(대사 확인)" : "PG 기록 없음(대사 확인)";
                if (finisher.fail(payment, reason, "reconciliation")) resolvedFailed++;
            }
        }

        Map<String, PgTransaction> pgApproved = pgServer.approvedTransactions().stream()
                .collect(Collectors.toMap(PgTransaction::getMerchantOrderKey, Function.identity()));
        Map<String, Payment> ours = paymentRepository.findAll().stream()
                .collect(Collectors.toMap(Payment::merchantOrderKey, Function.identity()));
        int pgButNotOurs = 0;
        int amountMismatches = 0;
        for (PgTransaction pg : pgApproved.values()) {
            Payment payment = ours.get(pg.getMerchantOrderKey());
            if (payment == null || payment.getStatus() != PaymentStatus.APPROVED) {
                // 방금 PG 호출이 끝나 아직 반영 전인 결제는 다음 대사에서 다시 본다
                if (payment != null && !payment.getStatus().isFinal()) continue;
                pgButNotOurs++;
                log.error("대사 불일치: PG 승인, 우리 쪽 {} (key={})", payment == null ? "결제 없음" : payment.getStatus(),
                        pg.getMerchantOrderKey());
            } else if (payment.getAmount() != pg.getAmount()) {
                amountMismatches++;
                log.error("대사 불일치: 금액 다름 key={}, 우리={}, PG={}", pg.getMerchantOrderKey(), payment.getAmount(), pg.getAmount());
            }
        }
        int oursButNotPg = 0;
        for (Payment payment : ours.values()) {
            if (payment.getStatus() == PaymentStatus.APPROVED && !pgApproved.containsKey(payment.merchantOrderKey())) {
                oursButNotPg++;
                log.error("대사 불일치: 우리 승인, PG 기록 없음 (paymentId={})", payment.getId());
            }
        }

        ReconciliationReport report = new ReconciliationReport(Instant.now(), resolvedApproved, resolvedFailed,
                open.size() - resolvedApproved - resolvedFailed, pgButNotOurs, oursButNotPg, amountMismatches,
                ledgerRepository.sumByDirection(LedgerEntry.Direction.DEBIT),
                ledgerRepository.sumByDirection(LedgerEntry.Direction.CREDIT),
                ledgerRepository.countApprovedWithoutLedger(), paymentRepository.sumApprovedAmount());
        last.set(report);
        if (report.mismatches() > 0) {
            log.error("대사 결과 불일치 {}건: {}", report.mismatches(), report);
        } else if (resolvedApproved + resolvedFailed > 0) {
            log.info("대사: 끝나지 않은 결제 {}건 정리(승인 {}, 실패 {})", resolvedApproved + resolvedFailed, resolvedApproved, resolvedFailed);
        }
        return report;
    }

    public Optional<ReconciliationReport> last() {
        return Optional.ofNullable(last.get());
    }
}
