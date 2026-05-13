package com.example.bootstrap.account.infrastructure.oauth2;

/**
 * 소셜 로그인 성공 시 반환되는 JWT 토큰 응답 DTO.
 *
 * @param accessToken  JWT Access Token (HS256, 30분 TTL)
 * @param refreshToken JWT Refresh Token (HS256, 14일 TTL)
 */
public record AuthTokenResponse(String accessToken, String refreshToken) {
}
