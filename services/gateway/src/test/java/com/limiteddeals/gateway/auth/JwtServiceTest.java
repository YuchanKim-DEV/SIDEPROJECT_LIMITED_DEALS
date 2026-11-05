package com.limiteddeals.gateway.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.PlainHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 9단계: 토큰을 위조하거나, 다른 키로 만들거나, 만료되면 통과하지 못한다. */
class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123";
    private final Instant now = Instant.parse("2026-11-05T00:00:00Z");
    private final JwtService service = new JwtService(SECRET, Duration.ofMinutes(30), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void 발급한_토큰은_통과하고_주인과_권한이_나온다() {
        String token = service.issue("user-1", Role.USER);

        assertThat(service.verify(token)).contains(new AuthUser("user-1", Role.USER));
    }

    @Test
    void 내용을_고치면_서명이_맞지_않아_거절한다() {
        String token = service.issue("user-1", Role.USER);
        String[] parts = token.split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"user-1\",\"iss\":\"limited-deals\",\"role\":\"ADMIN\",\"exp\":9999999999}".getBytes());

        assertThat(service.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    void 다른_키로_만든_토큰은_거절한다() {
        JwtService other = new JwtService("another-secret-another-secret-012345", Duration.ofMinutes(30),
                Clock.fixed(now, ZoneOffset.UTC));

        assertThat(service.verify(other.issue("user-1", Role.ADMIN))).isEmpty();
    }

    @Test
    void 서명_없는_토큰_alg_none은_거절한다() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("admin").issuer("limited-deals").claim("role", "ADMIN")
                .expirationTime(Date.from(now.plusSeconds(600))).build();

        assertThat(service.verify(new PlainJWT(new PlainHeader(), claims).serialize())).isEmpty();
    }

    @Test
    void 만료된_토큰은_거절한다() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("user-1").issuer("limited-deals").claim("role", "USER")
                .expirationTime(Date.from(now.minusSeconds(31))).build(); // 허용 오차 30초를 넘김
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));

        assertThat(service.verify(jwt.serialize())).isEmpty();
    }

    @Test
    void 짧은_키는_시작할_때_거절한다() {
        assertThatThrownBy(() -> new JwtService("short", Duration.ofMinutes(30), Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
