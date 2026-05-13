package com.example.bootstrap.account.application.dto;

import com.example.bootstrap.account.domain.model.Account;

import java.time.LocalDateTime;

/**
 * 계정 응답 DTO.
 *
 * <p>{@link Account} 엔티티를 API 응답 형식으로 변환합니다.
 * 비밀번호 등 민감 정보는 포함하지 않습니다.
 *
 * @param id              계정 PK
 * @param email           이메일 주소
 * @param nickname        닉네임
 * @param role            권한 역할 (USER 또는 ADMIN)
 * @param emailVerified   이메일 인증 여부
 * @param profileImageUrl 프로필 이미지 URL (nullable)
 * @param createdAt       생성일시
 * @param updatedAt       수정일시
 */
public record AccountResponse(
        Long id,
        String email,
        String nickname,
        String role,
        boolean emailVerified,
        String profileImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * {@link Account} 엔티티로부터 응답 DTO를 생성합니다.
     *
     * @param account 변환할 계정 엔티티
     * @return 응답 DTO
     */
    public static AccountResponse from(final Account account) {
        return new AccountResponse(
                account.getId(),
                account.getEmail(),
                account.getNickname(),
                account.getRole(),
                account.isEmailVerified(),
                account.getProfileImageUrl(),
                account.getCreatedAt(),
                account.getUpdatedAt());
    }
}
