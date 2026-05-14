package com.example.bootstrap.account.application.dto;

/**
 * JWT 토큰 응답 DTO.
 *
 * @param accessToken  단기 액세스 토큰 (30분 만료)
 * @param refreshToken 장기 리프레시 토큰 (14일 만료)
 */
public record TokenResponse(String accessToken, String refreshToken) {
}
