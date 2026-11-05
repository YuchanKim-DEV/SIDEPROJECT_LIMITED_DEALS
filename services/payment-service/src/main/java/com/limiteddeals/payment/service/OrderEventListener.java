package com.limiteddeals.payment.service;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** order-events(주문 접수)를 읽어 결제한다. notification-service와 그룹이 달라서 같은 이벤트를 각자 받는다. */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final PaymentService paymentService;

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void onMessage(ConsumerRecord<String, String> record) {
        paymentService.handleOrderPlaced(record.value());
    }
}
