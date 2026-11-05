package com.limiteddeals.order.payment;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** payment-events 토픽(결제 결과)을 읽는다. 처리에 성공해야 오프셋이 커밋된다. */
@Component
@RequiredArgsConstructor
public class PaymentResultListener {

    private final PaymentResultHandler handler;

    @KafkaListener(topics = "payment-events", groupId = "order-service")
    public void onMessage(ConsumerRecord<String, String> record) {
        handler.handle(record.value());
    }
}
