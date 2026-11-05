package com.limiteddeals.gateway.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * 로그인 토큰(JWT, HS256). 게이트웨이만 비밀 키를 알고, 만들기와 검증을 모두 여기서 한다.
 * 뒤쪽 서버는 토큰을 보지 않고, 게이트웨이가 검증 후 붙여 주는 X-User-Id, X-User-Role만 믿는다.
 *
 * 검증 규칙: 알고리즘이 HS256인지(헤더의 alg를 바꿔 "none"으로 보내는 공격 방지), 서명, 발급자, 만료 시각(30초 오차 허용).
 */
@Slf4j
@Component
public class JwtService {

    private static final String ISSUER = "limited-deals";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final String DEV_SECRET = "local-dev-only-secret-do-not-use-in-prod-0123456789";

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public JwtService(@Value("${limited-deals.auth.jwt-secret:}") String secret,
                      @Value("${limited-deals.auth.token-ttl:30m}") Duration ttl) {
        this(secret, ttl, Clock.systemUTC());
    }

    JwtService(String secret, Duration ttl, Clock clock) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET이 없어 로컬 개발용 키를 씁니다. 운영에서는 반드시 JWT_SECRET을 주세요.");
            secret = DEV_SECRET;
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 합니다(HS256)");
        }
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(String userId, Role role) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer(ISSUER)
                .claim("role", role.name())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("토큰 서명 실패", e);
        }
    }

    /** 올바른 토큰이면 사용자 정보, 아니면 비어 있음. 이유는 로그로만 남기고 응답에는 알리지 않는다. */
    public Optional<AuthUser> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expires = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer()) || expires == null
                    || expires.toInstant().plus(CLOCK_SKEW).isBefore(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(new AuthUser(claims.getSubject(), Role.valueOf(claims.getStringClaim("role"))));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    public Duration ttl() {
        return ttl;
    }
}
