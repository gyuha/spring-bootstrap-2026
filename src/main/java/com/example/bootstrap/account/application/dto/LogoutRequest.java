package com.example.bootstrap.account.application.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그아웃 요청 DTO.
 *
 * @param refreshToken 삭제할 리프레시 토큰
 */
public record LogoutRequest(@NotBlank String refreshToken) {
}
