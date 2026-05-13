package com.example.bootstrap.account.infrastructure.oauth2;

/**
 * OAuth2 provider로부터 수신한 소셜 사용자 정보 DTO.
 *
 * <p>Google, Kakao 등 각 provider의 userinfo 응답을 동일한 형식으로 정규화합니다.
 *
 * @param provider       OAuth2 provider 식별자 (google 또는 kakao)
 * @param providerId     provider 측 고유 사용자 ID
 * @param email          사용자 이메일 주소 (nullable — provider가 미제공 시 null)
 * @param nickname       사용자 닉네임 또는 이름
 * @param profileImageUrl 프로필 이미지 URL (nullable)
 */
public record SocialUserInfo(
        String provider,
        String providerId,
        String email,
        String nickname,
        String profileImageUrl) {
}
