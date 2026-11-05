package com.limiteddeals.payment.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 결제 결과 토픽(payment-events): 주문 id를 키로 써서 같은 주문의 결과는 같은 파티션(순서 보장)으로 간다.
 * 처리 실패 메시지: 1초 간격 3번 재시도 후 order-events-dlt로 옮긴다(notification-service와 같은 방식).
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name("payment-events").partitions(3).replicas(1).build();
    }

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer toDeadLetter = new DeadLetterPublishingRecoverer(template);
        return new DefaultErrorHandler((record, exception) -> {
            log.error("재시도를 모두 실패해 DLT로 옮김: topic={}, partition={}, offset={}, 원인={}",
                    record.topic(), record.partition(), record.offset(), exception.getMessage());
            toDeadLetter.accept(record, exception);
        }, new FixedBackOff(1000L, 3));
    }
}
