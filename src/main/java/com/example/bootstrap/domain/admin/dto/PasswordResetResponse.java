package com.example.bootstrap.domain.admin.dto;

/**
 * 패스워드 리셋 1회 응답 DTO (ADMIN-03, D-52).
 *
 * <p>서버가 생성한 임시 평문 패스워드를 응답 본문으로 <b>1회만</b> 반환한다. 평문은 이 record 외
 * 어디에도(로그/Redis/DB) 저장하지 않으며, DB에는 BCrypt 해시만 남는다(T-03-04 완화).
 *
 * @param temporaryPassword 서버 생성 임시 평문 패스워드(1회 노출)
 */
public record PasswordResetResponse(String temporaryPassword) {
}
