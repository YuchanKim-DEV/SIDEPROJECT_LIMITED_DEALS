package com.limiteddeals.gateway.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 모든 요청의 입구 검사(라우팅, Rate Limit보다 먼저).
 * 1) 손님이 보낸 X-User-Id, X-User-Role은 무조건 지운다. 예전에는 이 헤더를 그대로 믿어서 누구나 남인 척할 수 있었다.
 * 2) 경로 규칙(AccessRules)에 따라 토큰을 검사한다. 없거나 틀리면 401, 권한이 모자라면 403.
 * 3) 통과하면 토큰 주인을 X-User-Id, X-User-Role로 붙여 뒤쪽 서버로 보낸다. Rate Limit도 이 값으로 센다.
 * SSE(/stream)는 브라우저 EventSource가 헤더를 못 보내서 ?access_token= 으로도 받는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuthFilter implements WebFilter {

    static final String USER_HEADER = "X-User-Id";
    static final String ROLE_HEADER = "X-User-Role";

    private final JwtService jwtService;

    @Value("${limited-deals.auth.enabled:true}")
    private boolean enabled;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange); // 3~6단계 실험 스크립트를 예전 방식(X-User-Id)으로 다시 돌릴 때만
        }
        ServerHttpRequest request = exchange.getRequest();
        AccessRules.Access access = AccessRules.of(request.getMethod(), request.getPath().value());
        if (access == AccessRules.Access.INTERNAL_ONLY) {
            return reject(exchange, HttpStatus.NOT_FOUND, "없는 경로입니다.");
        }
        Optional<AuthUser> user = token(request).flatMap(jwtService::verify);
        if (access == AccessRules.Access.USER && user.isEmpty()
                || access == AccessRules.Access.ADMIN && user.isEmpty()) {
            return reject(exchange, HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (access == AccessRules.Access.ADMIN && user.get().role() != Role.ADMIN) {
            return reject(exchange, HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }
        ServerHttpRequest trusted = request.mutate().headers(headers -> {
            headers.remove(USER_HEADER);
            headers.remove(ROLE_HEADER);
            user.ifPresent(u -> {
                headers.set(USER_HEADER, u.userId());
                headers.set(ROLE_HEADER, u.role().name());
            });
        }).build();
        return chain.filter(exchange.mutate().request(trusted).build());
    }

    private static Optional<String> token(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return Optional.of(header.substring(7).trim());
        }
        if (request.getPath().value().endsWith("/stream")) {
            return Optional.ofNullable(request.getQueryParams().getFirst("access_token"));
        }
        return Optional.empty();
    }

    private static Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }
}
