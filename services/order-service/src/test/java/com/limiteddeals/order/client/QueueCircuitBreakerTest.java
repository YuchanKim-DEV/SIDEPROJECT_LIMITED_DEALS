package com.limiteddeals.order.client;

import com.limiteddeals.order.exception.QueueUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 5단계: 대기열 서버가 계속 죽어 있으면 회로가 열리고, 열린 뒤에는 호출하지 않고 바로 거절한다. */
@SpringBootTest(properties = {
        "limited-deals.order.entry-token.required=true",
        "limited-deals.order.entry-token.queue-url=http://localhost:1"
})
class QueueCircuitBreakerTest {

    @Autowired EntryTokenVerifier verifier;
    @Autowired CircuitBreakerRegistry registry;

    @Test
    void 실패가_쌓이면_회로가_열리고_바로_거절한다() {
        // 회로는 첫 호출 때 우리 설정(최소 10번)으로 만들어진다. 테스트가 먼저 registry에서 꺼내면 기본 설정(최소 100번)으로 만들어지므로 순서를 지킨다.
        assertThatThrownBy(() -> verifier.verify(1, "u", "t")).isInstanceOf(QueueUnavailableException.class);
        CircuitBreaker breaker = registry.circuitBreaker("queue-service");
        breaker.reset();

        for (int i = 0; i < 10; i++) {
            assertThatThrownBy(() -> verifier.verify(1, "u", "t")).isInstanceOf(QueueUnavailableException.class);
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        long notPermittedBefore = breaker.getMetrics().getNumberOfNotPermittedCalls();
        assertThatThrownBy(() -> verifier.verify(1, "u", "t")).isInstanceOf(QueueUnavailableException.class);
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(notPermittedBefore + 1); // 호출하지 않고 막았다
    }
}
