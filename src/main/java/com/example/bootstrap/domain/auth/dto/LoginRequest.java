package com.example.bootstrap.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청 (AUTH-02).
 *
 * @param email    회원 email
 * @param password 평문 비밀번호
 */
public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password) {
}
