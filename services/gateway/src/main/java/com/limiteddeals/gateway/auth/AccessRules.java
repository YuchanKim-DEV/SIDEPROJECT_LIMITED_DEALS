package com.limiteddeals.gateway.auth;

import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 경로별 접근 규칙. 위에서부터 처음 맞는 규칙을 쓴다. 어디에도 안 맞는 /api/** 는 로그인 필요(기본은 막는다).
 */
public final class AccessRules {

    public enum Access { PUBLIC, USER, ADMIN, INTERNAL_ONLY }

    private record Rule(HttpMethod method, String pattern, Access access) {
    }

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final List<Rule> RULES = List.of(
            // 서버끼리만 쓰는 입장권 확인. 밖에서 부르면 남의 입장권을 맞춰 보는 데 쓸 수 있다
            new Rule(null, "/api/queue/*/tokens/validate", Access.INTERNAL_ONLY),
            new Rule(null, "/api/auth/**", Access.PUBLIC),
            new Rule(HttpMethod.GET, "/api/products/**", Access.PUBLIC),
            new Rule(HttpMethod.GET, "/api/queue/*/stats", Access.PUBLIC),
            // 운영자 기능
            new Rule(null, "/api/*/admin/**", Access.ADMIN),
            new Rule(null, "/api/queue/*/simulate", Access.ADMIN),
            new Rule(HttpMethod.POST, "/api/products", Access.ADMIN),
            new Rule(null, "/api/notifications/**", Access.ADMIN),
            new Rule(null, "/api/payments/reconciliation", Access.ADMIN),
            new Rule(null, "/api/payments/settlement", Access.ADMIN),
            new Rule(null, "/api/payments/stats", Access.ADMIN),
            new Rule(null, "/api/**", Access.USER)
    );

    private AccessRules() {
    }

    public static Access of(HttpMethod method, String path) {
        for (Rule rule : RULES) {
            if ((rule.method() == null || rule.method().equals(method)) && MATCHER.match(rule.pattern(), path)) {
                return rule.access();
            }
        }
        return Access.PUBLIC; // /api 밖: 테스트 화면, actuator, fallback
    }
}
