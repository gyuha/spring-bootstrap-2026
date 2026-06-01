package com.example.bootstrap.domain.auth.dto;

/**
 * 로그인/재발급 응답 — Access + Refresh 토큰 (AUTH-02/03).
 *
 * @param accessToken  HS256 Access JWT
 * @param refreshToken opaque Refresh 토큰 {@code {userId}.{familyId}.{secret}}
 */
public record TokenResponse(String accessToken, String refreshToken) {
}
