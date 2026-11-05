package com.limiteddeals.payment.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Inbox: 이미 처리한 이벤트 id를 기록한다. Kafka는 같은 메시지를 두 번 이상 보낼 수 있으므로(at-least-once),
 * 처리하기 전에 여기서 확인해서 두 번째부터는 건너뛴다. event_id의 유니크 제약이 마지막 방어선이다.
 * 처리 결과(Payment)와 같은 트랜잭션으로 저장해야, "기록은 했는데 처리는 안 된" 상태가 생기지 않는다.
 */
@Entity
@Table(name = "payment_inbox", uniqueConstraints = @UniqueConstraint(name = "uk_payment_inbox_event_id", columnNames = "event_id"))
@Getter
@NoArgsConstructor
public class InboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    public InboxEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }
}
