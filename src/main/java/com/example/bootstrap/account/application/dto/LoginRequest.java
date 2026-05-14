package com.example.bootstrap.account.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * 이메일/비밀번호 로그인 요청 DTO.
 *
 * @param email    로그인 이메일 주소
 * @param password 평문 비밀번호
 */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
