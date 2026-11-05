package com.limiteddeals.payment.service;

import com.limiteddeals.payment.domain.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 정산: 하루 동안의 매출을 원장에서 모아, PG 수수료를 빼고 실제로 받을 돈을 계산한다.
 * 결제 테이블이 아니라 원장에서 계산한다. 원장은 추가만 되는 기록이라, 나중에 다시 계산해도 같은 값이 나온다.
 * 금액은 원 단위 정수(long)로 다룬다. 소수(double)는 오차가 생겨 돈 계산에 쓰지 않는다. 수수료는 내림.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final LedgerEntryRepository ledgerRepository;

    @Value("${limited-deals.payment.pg-fee-basis-points:300}")
    private long feeBasisPoints; // 300 = 3.00%

    public Settlement settle(LocalDate date) {
        Instant from = date.atStartOfDay(KST).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(KST).toInstant();
        long gross = ledgerRepository.sumSales(from, to);
        long count = ledgerRepository.countSales(from, to);
        long fee = gross * feeBasisPoints / 10_000;
        return new Settlement(date, count, gross, fee, gross - fee);
    }

    /** date: 정산일(한국 시간), count: 승인 건수, gross: 매출, fee: PG 수수료, net: 받을 돈 */
    public record Settlement(LocalDate date, long count, long gross, long fee, long net) {
    }
}
