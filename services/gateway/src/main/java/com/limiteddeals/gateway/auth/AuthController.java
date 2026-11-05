package com.limiteddeals.gateway.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * 토큰 발급.
 * dev-login: 회원 가입·비밀번호 없이 userId만으로 토큰을 준다. 부하 테스트(k6)와 테스트 화면용이고 운영에서는 끈다
 *            (DEV_LOGIN_ENABLED=false). 실제 서비스라면 회원 서버가 비밀번호나 소셜 로그인으로 확인한 뒤 발급한다.
 * admin-login: ADMIN_PASSWORD를 설정했을 때만 열린다.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;
    private final boolean devLoginEnabled;
    private final byte[] adminPassword;

    public AuthController(JwtService jwtService,
                          @Value("${limited-deals.auth.dev-login-enabled:true}") boolean devLoginEnabled,
                          @Value("${limited-deals.auth.admin-password:}") String adminPassword) {
        this.jwtService = jwtService;
        this.devLoginEnabled = devLoginEnabled;
        this.adminPassword = adminPassword.getBytes(StandardCharsets.UTF_8);
    }

    /** userId: 영문, 숫자, -, _ 만 1~50자. 로그와 Redis 키에 그대로 들어가므로 이상한 문자를 막는다. */
    public record DevLoginRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,50}") String userId) {
    }

    public record AdminLoginRequest(@NotBlank String password) {
    }

    @PostMapping("/dev-login")
    public ResponseEntity<Map<String, Object>> devLogin(@Valid @RequestBody DevLoginRequest request) {
        if (!devLoginEnabled) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(tokenBody(request.userId(), Role.USER));
    }

    @PostMapping("/admin-login")
    public ResponseEntity<Map<String, Object>> adminLogin(@Valid @RequestBody AdminLoginRequest request) {
        if (adminPassword.length == 0) {
            return ResponseEntity.notFound().build();
        }
        // 글자를 앞에서부터 비교하다 다르면 바로 끝내는 equals는, 응답 시간 차이로 비밀번호를 한 글자씩 맞힐 수 있다
        if (!MessageDigest.isEqual(adminPassword, request.password().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).body(Map.of("message", "비밀번호가 틀렸습니다."));
        }
        return ResponseEntity.ok(tokenBody("admin", Role.ADMIN));
    }

    private Map<String, Object> tokenBody(String userId, Role role) {
        return Map.of("accessToken", jwtService.issue(userId, role), "tokenType", "Bearer",
                "expiresInSeconds", jwtService.ttl().toSeconds(), "userId", userId, "role", role);
    }
}
