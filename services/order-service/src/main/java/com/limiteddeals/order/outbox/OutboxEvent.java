package com.limiteddeals.order.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Outbox: "보낼 이벤트"를 업무 데이터(주문)와 같은 DB 트랜잭션으로 저장하는 테이블.
 * 주문이 커밋되면 이벤트도 반드시 남고, 주문이 취소되면 이벤트도 남지 않는다.
 * 실제 Kafka 발행은 OutboxRelay가 따로 한다(DB와 Kafka를 한 트랜잭션으로 묶을 수 없어서).
 */
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_status_id", columnList = "status, id"))
@Getter
@NoArgsConstructor
public class OutboxEvent {

    public enum Status { PENDING, SENT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 받는 쪽이 중복을 걸러낼 때 쓰는 이벤트 고유 번호. */
    @Column(nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String eventType;

    /** Kafka 메시지 키. 같은 키는 같은 파티션으로 가서 순서가 보장된다(상품 id). */
    @Column(nullable = false, length = 50)
    private String messageKey;

    @Lob
    @Column(nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    public OutboxEvent(String eventId, String eventType, String messageKey, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.messageKey = messageKey;
        this.payload = payload;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }
}
