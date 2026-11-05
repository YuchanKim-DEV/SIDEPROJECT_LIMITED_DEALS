package com.limiteddeals.notification.service;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * order-events 토픽을 읽는다. 처리에 성공해야 오프셋(어디까지 읽었는지)이 커밋된다.
 * 그래서 이 서버가 죽었다가 살아나면, 마지막으로 커밋한 곳부터 다시 읽어 빠진 메시지 없이 이어간다.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderEventHandler handler;

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void onMessage(ConsumerRecord<String, String> record) {
        handler.handle(record.value());
    }
}
