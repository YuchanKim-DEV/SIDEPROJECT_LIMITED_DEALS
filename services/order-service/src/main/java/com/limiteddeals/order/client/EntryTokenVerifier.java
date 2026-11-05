package com.limiteddeals.order.client;

import com.limiteddeals.order.exception.EntryTokenRejectedException;
import com.limiteddeals.order.exception.QueueUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * 주문 전에 queue-service에 "이 사람이 입장권을 가졌는지" 묻는다.
 * 대기열을 건너뛰고 주문 API를 직접 부르는 것을 막는다.
 *
 * 장애 대응:
 * - 연결 300ms, 응답 500ms를 넘기면 포기한다(느린 대기열 서버 때문에 주문 스레드가 오래 묶이지 않게).
 * - 서킷 브레이커가 실패와 느린 호출을 세다가, 너무 많으면 회로를 열고 한동안 아예 호출하지 않는다.
 * - 대기열 서버를 확인할 수 없으면 주문을 받지 않는다(fail closed). 확인 없이 받으면 대기열이 무의미해지기 때문이다.
 * 403(입장권이 틀림)은 정상 응답이라 서킷 브레이커의 실패로 세지 않는다.
 */
@Slf4j
@Component
public class EntryTokenVerifier {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;

    public EntryTokenVerifier(@Value("${limited-deals.order.entry-token.queue-url:http://localhost:8081}") String queueUrl,
                              @Value("${limited-deals.order.entry-token.connect-timeout-ms:300}") int connectTimeoutMs,
                              @Value("${limited-deals.order.entry-token.read-timeout-ms:500}") int readTimeoutMs,
                              CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder().baseUrl(queueUrl).requestFactory(factory).build();
        this.circuitBreaker = circuitBreakerFactory.create("queue-service");
    }

    public void verify(long productId, String userId, String token) {
        if (userId == null || userId.isBlank() || token == null || token.isBlank()) {
            throw new EntryTokenRejectedException("입장권이 없습니다. 대기열에서 입장권을 받아 주세요.");
        }
        int status = circuitBreaker.run(() -> callQueue(productId, userId, token), failure -> {
            log.warn("대기열 확인 실패(회로 열림 또는 호출 실패): {}", failure.toString());
            throw new QueueUnavailableException();
        });
        if (status == 403) {
            throw new EntryTokenRejectedException("입장권이 유효하지 않거나 만료됐습니다.");
        }
    }

    /** 200 또는 403을 돌려준다. 그 밖의 응답과 연결 실패·시간 초과는 예외로 던져 서킷 브레이커가 실패로 센다. */
    private int callQueue(long productId, String userId, String token) {
        int status = restClient.get()
                .uri("/api/queue/{productId}/tokens/validate?userId={userId}&token={token}", productId, userId, token)
                .exchange((request, response) -> response.getStatusCode().value());
        if (status != 200 && status != 403) {
            throw new IllegalStateException("대기열 서버 응답 이상: " + status);
        }
        return status;
    }
}
