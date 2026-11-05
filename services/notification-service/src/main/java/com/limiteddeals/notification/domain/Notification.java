package com.limiteddeals.notification.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** 보낸 알림. 실제로 문자나 푸시를 보내는 대신 기록만 남긴다. */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 36)
    private String eventId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long productId;

    @Column(length = 50)
    private String userId;

    @Column(nullable = false, length = 200)
    private String message;

    @Column(nullable = false)
    private Instant createdAt;

    /** 주문이 생긴 시각부터 알림을 보낸 시각까지(이벤트 전달 지연). */
    @Column(nullable = false)
    private long delayMillis;

    public Notification(String eventId, Long orderId, Long productId, String userId, String message, long delayMillis) {
        this.eventId = eventId;
        this.orderId = orderId;
        this.productId = productId;
        this.userId = userId;
        this.message = message;
        this.createdAt = Instant.now();
        this.delayMillis = delayMillis;
    }
}
