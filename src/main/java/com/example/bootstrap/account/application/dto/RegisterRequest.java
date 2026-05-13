package com.example.bootstrap.account.application.dto;

/**
 * 회원 가입 요청 DTO.
 *
 * <p>이메일, 비밀번호, 닉네임을 포함한 신규 계정 생성 요청 데이터를 담습니다.
 * 비밀번호는 서비스 레이어에서 BCrypt 인코딩됩니다.
 *
 * @param email    이메일 주소 (로그인 식별자, unique)
 * @param password 평문 비밀번호 (BCrypt 인코딩 전)
 * @param nickname 사용자 닉네임
 */
public record RegisterRequest(String email, String password, String nickname) {
}
