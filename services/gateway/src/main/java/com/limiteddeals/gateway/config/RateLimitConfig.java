package com.limiteddeals.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Rate Limit의 "누구 기준으로 셀지"를 정한다.
 * userKeyResolver: 사용자마다 따로 센다. 한 사람이(봇이) 버튼을 미친 듯이 누르는 것을 막는다.
 * globalKeyResolver: 모두 합쳐서 센다. 뒤쪽 서버가 버틸 수 있는 전체 처리량을 넘지 않게 막는다.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @Primary
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(Optional.ofNullable(exchange.getRequest().getHeaders().getFirst("X-User-Id"))
                .orElseGet(() -> Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                        .map(InetSocketAddress::getHostString).orElse("unknown")));
    }

    @Bean
    public KeyResolver globalKeyResolver() {
        return exchange -> Mono.just("global");
    }
}
