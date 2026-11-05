package com.limiteddeals.order.client;

import com.limiteddeals.order.exception.EntryTokenRejectedException;
import com.limiteddeals.order.exception.QueueUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 주문 전에 queue-service에 "이 사람이 입장권을 가졌는지" 묻는다.
 * 대기열을 건너뛰고 주문 API를 직접 부르는 것을 막는다.
 * 대기열 서버가 응답하지 않으면 주문을 받지 않는다(fail closed). 확인 없이 받으면 대기열이 무의미해지기 때문이다.
 */
@Slf4j
@Component
public class EntryTokenVerifier {

    private final RestClient restClient;

    public EntryTokenVerifier(@Value("${limited-deals.order.entry-token.queue-url:http://localhost:8081}") String queueUrl,
                              @Value("${limited-deals.order.entry-token.connect-timeout-ms:300}") int connectTimeoutMs,
                              @Value("${limited-deals.order.entry-token.read-timeout-ms:500}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs)); // 대기열 서버가 느려도 주문 스레드가 오래 묶이지 않게
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().baseUrl(queueUrl).requestFactory(factory).build();
    }

    public void verify(long productId, String userId, String token) {
        if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
            throw new EntryTokenRejectedException("입장권이 없습니다. 대기열에서 입장권을 받아 주세요.");
        }
        HttpStatusCode status;
        try {
            status = restClient.get()
                    .uri("/api/queue/{productId}/tokens/validate?userId={userId}&token={token}", productId, userId, token)
                    .exchange((request, response) -> response.getStatusCode());
        } catch (ResourceAccessException timeoutOrRefused) {
            log.warn("대기열 서버 응답 없음: {}", timeoutOrRefused.getMessage());
            throw new QueueUnavailableException();
        }
        if (status.value() == 403) {
            throw new EntryTokenRejectedException("입장권이 유효하지 않거나 만료됐습니다.");
        }
        if (!status.is2xxSuccessful()) {
            throw new QueueUnavailableException();
        }
    }
}
