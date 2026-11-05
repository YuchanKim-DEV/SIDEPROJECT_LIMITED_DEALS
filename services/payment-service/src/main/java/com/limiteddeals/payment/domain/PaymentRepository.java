package com.limiteddeals.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    /**
     * 끝나지 않은 결제만 다음 상태로 바꾼다. 바뀐 줄 수를 돌려준다(1이면 내가 바꿨다, 0이면 누가 먼저 끝냈다).
     * 컨슈머와 대사 배치가 같은 결제를 동시에 끝내려 해도, 원장 기록과 이벤트 발행은 이긴 한쪽만 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p set p.status = :next, p.pgTransactionId = :pgTransactionId, p.failureReason = :reason,
                   p.updatedAt = :now
             where p.id = :id and p.status in :from""")
    int transition(@Param("id") Long id, @Param("from") Collection<PaymentStatus> from, @Param("next") PaymentStatus next,
                   @Param("pgTransactionId") String pgTransactionId, @Param("reason") String reason, @Param("now") Instant now);

    /** 대사 대상: 오래 끝나지 않은 결제(서버가 PG 호출 중에 죽었거나, PG 응답을 못 받은 경우). */
    List<Payment> findByStatusInAndUpdatedAtBefore(Collection<PaymentStatus> statuses, Instant before);

    List<Payment> findByStatus(PaymentStatus status);

    long countByStatus(PaymentStatus status);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = com.limiteddeals.payment.domain.PaymentStatus.APPROVED")
    long sumApprovedAmount();
}
