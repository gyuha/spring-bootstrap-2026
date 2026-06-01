package com.example.bootstrap.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가입 요청 (AUTH-01).
 *
 * @param email    회원 email (형식 검증)
 * @param password 평문 비밀번호 (최소 8자, 서버에서 BCrypt 해싱)
 */
public record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password) {
}
