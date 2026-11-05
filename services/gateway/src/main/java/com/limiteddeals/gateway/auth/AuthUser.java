package com.limiteddeals.gateway.auth;

/** 검증된 토큰의 주인. */
public record AuthUser(String userId, Role role) {
}
