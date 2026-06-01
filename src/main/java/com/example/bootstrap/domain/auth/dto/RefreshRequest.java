package com.example.bootstrap.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Access 재발급 요청 (AUTH-03).
 *
 * @param refreshToken opaque Refresh 토큰 {@code {userId}.{familyId}.{secret}}
 */
public record RefreshRequest(@NotBlank String refreshToken) {
}
