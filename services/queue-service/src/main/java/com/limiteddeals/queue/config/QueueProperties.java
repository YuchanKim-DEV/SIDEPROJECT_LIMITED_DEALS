package com.limiteddeals.queue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 대기열 설정.
 * admitPerSecond: 1초마다 줄 맨 앞에서 입장시키는 인원. 뒤쪽 주문 서버가 감당할 수 있는 만큼으로 정한다.
 * tokenTtlSeconds: 입장권 유효 시간. 이 안에 구매하지 않으면 사라진다.
 */
@ConfigurationProperties(prefix = "limited-deals.queue")
public record QueueProperties(int admitPerSecond, long tokenTtlSeconds) {

    public QueueProperties {
        if (admitPerSecond <= 0) admitPerSecond = 100;
        if (tokenTtlSeconds <= 0) tokenTtlSeconds = 300;
    }
}
