package com.example.bootstrap.account.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 토큰 갱신 요청 DTO.
 *
 * @param refreshToken 재발급에 사용할 리프레시 토큰
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
