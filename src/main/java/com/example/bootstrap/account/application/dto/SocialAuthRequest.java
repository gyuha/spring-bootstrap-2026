package com.example.bootstrap.account.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 소셜 로그인 요청 DTO.
 *
 * @param accessToken OAuth2 프로바이더로부터 발급받은 액세스 토큰
 */
public record SocialAuthRequest(@NotBlank String accessToken) {
}
