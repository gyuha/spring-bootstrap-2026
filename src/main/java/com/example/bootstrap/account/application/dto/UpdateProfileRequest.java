package com.example.bootstrap.account.application.dto;

/**
 * 프로필 업데이트 요청 DTO.
 *
 * <p>닉네임 및 프로필 이미지 URL 변경 요청 데이터를 담습니다.
 *
 * @param nickname        변경할 닉네임
 * @param profileImageUrl 변경할 프로필 이미지 URL (nullable)
 */
public record UpdateProfileRequest(String nickname, String profileImageUrl) {
}
