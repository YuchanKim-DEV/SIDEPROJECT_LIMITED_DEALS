package com.limiteddeals.order.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /** 아직 안 보낸 이벤트를 오래된 순서대로. 순서대로 보내야 같은 상품의 이벤트 순서가 유지된다. */
    List<OutboxEvent> findByStatusOrderByIdAsc(OutboxEvent.Status status, Pageable pageable);

    long countByStatus(OutboxEvent.Status status);

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.status = com.limiteddeals.order.outbox.OutboxEvent.Status.SENT, e.sentAt = :sentAt where e.id = :id")
    void markSent(@Param("id") Long id, @Param("sentAt") Instant sentAt);

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.attempts = e.attempts + 1 where e.id = :id")
    void increaseAttempts(@Param("id") Long id);
}
