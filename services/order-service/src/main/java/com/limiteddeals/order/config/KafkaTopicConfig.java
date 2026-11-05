package com.limiteddeals.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** 주문 이벤트 토픽. 파티션 3개: 상품 id를 키로 써서 같은 상품의 이벤트는 같은 파티션(순서 보장)으로 간다. */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events").partitions(3).replicas(1).build();
    }

    /**
     * 계속 실패하는 메시지를 따로 모아두는 곳(DLQ, Dead Letter Topic).
     * Spring Kafka 4의 DeadLetterPublishingRecoverer는 기본으로 "원래 토픽-dlt"로 보낸다(예전 버전은 ".DLT").
     * 원래 토픽과 파티션 수를 같게 둬야, 실패한 메시지가 같은 번호의 파티션으로 간다.
     */
    @Bean
    public NewTopic orderEventsDeadLetterTopic() {
        return TopicBuilder.name("order-events-dlt").partitions(3).replicas(1).build();
    }
}
