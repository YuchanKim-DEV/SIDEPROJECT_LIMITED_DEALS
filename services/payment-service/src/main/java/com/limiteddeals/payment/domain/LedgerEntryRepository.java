package com.limiteddeals.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntry e where e.direction = :direction")
    long sumByDirection(@Param("direction") LedgerEntry.Direction direction);

    List<LedgerEntry> findByPaymentId(Long paymentId);

    /** 승인된 결제 중 원장 기록이 없는 결제 수(있으면 안 된다). */
    @Query("""
            select count(p) from Payment p
             where p.status = com.limiteddeals.payment.domain.PaymentStatus.APPROVED
               and not exists (select 1 from LedgerEntry e where e.paymentId = p.id)""")
    long countApprovedWithoutLedger();

    /** 정산: 기간 안에 생긴 매출(대변) 합과 건수. */
    @Query("""
            select coalesce(sum(e.amount), 0) from LedgerEntry e
             where e.account = com.limiteddeals.payment.domain.LedgerEntry.Account.SALES
               and e.direction = com.limiteddeals.payment.domain.LedgerEntry.Direction.CREDIT
               and e.createdAt >= :from and e.createdAt < :to""")
    long sumSales(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select count(e) from LedgerEntry e
             where e.account = com.limiteddeals.payment.domain.LedgerEntry.Account.SALES
               and e.direction = com.limiteddeals.payment.domain.LedgerEntry.Direction.CREDIT
               and e.createdAt >= :from and e.createdAt < :to""")
    long countSales(@Param("from") Instant from, @Param("to") Instant to);
}
