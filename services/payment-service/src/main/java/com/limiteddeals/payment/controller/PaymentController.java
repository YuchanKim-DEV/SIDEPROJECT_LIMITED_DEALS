package com.limiteddeals.payment.controller;

import com.limiteddeals.payment.domain.LedgerEntryRepository;
import com.limiteddeals.payment.domain.PaymentRepository;
import com.limiteddeals.payment.domain.PaymentStatus;
import com.limiteddeals.payment.pg.PgChaos;
import com.limiteddeals.payment.service.ReconciliationReport;
import com.limiteddeals.payment.service.ReconciliationService;
import com.limiteddeals.payment.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentRepository paymentRepository;
    private final LedgerEntryRepository ledgerRepository;
    private final ReconciliationService reconciliationService;
    private final SettlementService settlementService;
    private final PgChaos chaos;

    @Value("${limited-deals.payment.chaos-enabled:false}")
    private boolean chaosEnabled;

    /** 주문 하나의 결제 상태와 원장 기록. 내 결제만 볼 수 있다(관리자는 모두). 남의 것이면 404. */
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> byOrder(@PathVariable Long orderId,
                                                       @RequestHeader(name = "X-User-Id", required = false) String userId,
                                                       @RequestHeader(name = "X-User-Role", required = false) String role) {
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        return paymentRepository.findByOrderId(orderId)
                .filter(p -> "ADMIN".equals(role) || userId.equals(p.getUserId()))
                .<ResponseEntity<Map<String, Object>>>map(p -> {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("paymentId", p.getId());
            body.put("orderId", p.getOrderId());
            body.put("amount", p.getAmount());
            body.put("status", p.getStatus());
            body.put("pgTransactionId", p.getPgTransactionId());
            body.put("failureReason", p.getFailureReason());
            body.put("ledger", ledgerRepository.findByPaymentId(p.getId()).stream()
                    .map(e -> Map.of("account", e.getAccount(), "direction", e.getDirection(), "amount", e.getAmount())).toList());
            return ResponseEntity.ok(body);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** 상태별 결제 수. */
    @GetMapping("/stats")
    public Map<PaymentStatus, Long> stats() {
        Map<PaymentStatus, Long> counts = new LinkedHashMap<>();
        Arrays.stream(PaymentStatus.values()).forEach(s -> counts.put(s, paymentRepository.countByStatus(s)));
        return counts;
    }

    /** 대사를 지금 돌린다(원래는 30초마다 자동). */
    @PostMapping("/reconciliation")
    public ReconciliationReport reconcile() {
        return reconciliationService.run();
    }

    @GetMapping("/reconciliation")
    public ResponseEntity<ReconciliationReport> lastReconciliation() {
        return ResponseEntity.of(reconciliationService.last());
    }

    /** 하루 정산. date를 생략하면 오늘(한국 시간). */
    @GetMapping("/settlement")
    public SettlementService.Settlement settlement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return settlementService.settle(date != null ? date : LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    /** 실험용 PG 장애 설정. CHAOS_ENABLED=true로 띄웠을 때만 동작한다. */
    @PostMapping("/admin/pg")
    public ResponseEntity<Map<String, Object>> setPgChaos(@RequestParam(defaultValue = "0") double declineRate,
                                                          @RequestParam(defaultValue = "0") double timeoutRate,
                                                          @RequestParam(defaultValue = "20") long latencyMs) {
        if (!chaosEnabled) {
            return ResponseEntity.notFound().build();
        }
        chaos.set(declineRate, timeoutRate, latencyMs);
        return ResponseEntity.ok(Map.of("declineRate", declineRate, "timeoutRate", timeoutRate, "latencyMs", latencyMs));
    }
}
