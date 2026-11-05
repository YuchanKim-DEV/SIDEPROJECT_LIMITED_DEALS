package com.limiteddeals.notification.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * 처리에 실패한 메시지: 1초 간격으로 3번 더 시도하고, 그래도 실패하면 order-events-dlt(Dead Letter Topic)로 옮긴다.
 * (Spring Kafka 4는 기본으로 "원래 토픽-dlt"로 보낸다. 예전 버전의 ".DLT"가 아니다.)
 * 옮긴 뒤에는 다음 메시지로 넘어가서, 메시지 하나 때문에 뒤에 쌓인 메시지가 모두 막히는 일을 피한다.
 * DLT에 쌓인 메시지는 원인을 고친 뒤 다시 보내면 된다(Inbox 덕분에 다시 보내도 중복 처리되지 않는다).
 */
@Slf4j
@Configuration
public class KafkaErrorConfig {

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
