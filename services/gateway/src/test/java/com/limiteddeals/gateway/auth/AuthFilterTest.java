package com.limiteddeals.gateway.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** 9단계: 게이트웨이 입구 검사. 손님이 보낸 X-User-Id는 믿지 않고, 경로마다 필요한 권한을 확인한다. */
class AuthFilterTest {

    private final JwtService jwt = new JwtService("test-secret-test-secret-test-secret-0123", Duration.ofMinutes(30),
            Clock.systemUTC());
    private final AuthFilter filter = new AuthFilter(jwt);
    private final AtomicReference<ServerWebExchange> passed = new AtomicReference<>();

    AuthFilterTest() {
        ReflectionTestUtils.setField(filter, "enabled", true);
    }

    private MockServerWebExchange run(MockServerHttpRequest.BaseBuilder<?> request) {
        passed.set(null);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        filter.filter(exchange, e -> {
            passed.set(e);
            return Mono.empty();
        }).block();
        return exchange;
    }

    private String bearer(String user, Role role) {
        return "Bearer " + jwt.issue(user, role);
    }

    @Test
    void 토큰_없이_주문하면_401() {
        var exchange = run(MockServerHttpRequest.post("/api/orders").header("X-User-Id", "victim"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(passed.get()).isNull();
    }

    @Test
    void 손님이_보낸_X_User_Id는_지우고_토큰_주인으로_바꾼다() {
        run(MockServerHttpRequest.post("/api/orders")
                .header("Authorization", bearer("me", Role.USER))
                .header("X-User-Id", "victim")
                .header("X-User-Role", "ADMIN"));

        var headers = passed.get().getRequest().getHeaders();
        assertThat(headers.get("X-User-Id")).containsExactly("me");
        assertThat(headers.get("X-User-Role")).containsExactly("USER");
    }

    @Test
    void 공개_경로는_토큰_없이_통과하지만_가짜_헤더는_지운다() {
        run(MockServerHttpRequest.get("/api/products/1").header("X-User-Id", "victim"));

        assertThat(passed.get()).isNotNull();
        assertThat(passed.get().getRequest().getHeaders().get("X-User-Id")).isNull();
    }

    @Test
    void 일반_사용자는_관리자_기능에_403() {
        var chaos = run(MockServerHttpRequest.post("/api/queue/admin/chaos").header("Authorization", bearer("me", Role.USER)));
        var product = run(MockServerHttpRequest.post("/api/products").header("Authorization", bearer("me", Role.USER)));

        assertThat(chaos.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(product.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void 관리자는_관리자_기능을_쓸_수_있다() {
        run(MockServerHttpRequest.post("/api/products").header("Authorization", bearer("admin", Role.ADMIN)));

        assertThat(passed.get()).isNotNull();
    }

    @Test
    void 서버끼리_쓰는_입장권_확인은_밖에서_404() {
        var exchange = run(MockServerHttpRequest.get("/api/queue/1/tokens/validate?userId=a&token=b")
                .header("Authorization", bearer("admin", Role.ADMIN)));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void SSE는_쿼리의_토큰으로_로그인한다() {
        String token = jwt.issue("me", Role.USER);
        run(MockServerHttpRequest.get("/api/queue/1/stream?userId=victim&access_token=" + token));

        assertThat(passed.get().getRequest().getHeaders().get("X-User-Id")).containsExactly("me");
    }
}
