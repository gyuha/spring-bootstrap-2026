package com.example.bootstrap.account.infrastructure.oauth2;

import com.example.bootstrap.account.domain.repository.AccountRepository;
import com.example.bootstrap.account.domain.repository.RefreshTokenRepository;
import com.example.bootstrap.account.domain.repository.SocialAccountRepository;
import com.example.bootstrap.global.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Kakao OAuth2 userinfo 핸들러.
 *
 * <p>Kakao의 {@code /v2/user/me} 엔드포인트를 호출하여
 * 사용자 정보를 조회하고 JWT 토큰을 발급합니다.
 *
 * <p>클라이언트 주도 흐름: 프론트엔드가 Kakao access token을 획득한 후
 * {@code POST /api/v1/auth/social} 로 전달하면 이 핸들러가 처리합니다.
 */
@Component
public class KakaoOAuth2Handler extends AbstractOAuth2Handler {

    /** Kakao userinfo API 엔드포인트. */
    static final String USERINFO_URL = "https://kapi.kakao.com/v2/user/me";

    /** provider 식별자. */
    static final String PROVIDER = "kakao";

    /**
     * Kakao OAuth2 핸들러를 생성합니다. Spring이 사용하는 공개 생성자입니다.
     *
     * @param webClientBuilder       WebClient 빌더 (Spring Boot 자동 구성)
     * @param accountRepository      계정 리포지토리
     * @param socialAccountRepository 소셜 계정 리포지토리
     * @param refreshTokenRepository  Refresh Token 리포지토리
     * @param jwtTokenProvider        JWT 토큰 유틸
     */
    @Autowired
    public KakaoOAuth2Handler(
            WebClient.Builder webClientBuilder,
            AccountRepository accountRepository,
            SocialAccountRepository socialAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider) {
        super(
                webClientBuilder.build(),
                accountRepository,
                socialAccountRepository,
                refreshTokenRepository,
                jwtTokenProvider);
    }

    /**
     * 단위 테스트용 패키지-프라이빗 생성자.
     *
     * <p>사전에 구성된 {@link WebClient}(모의 ExchangeFunction 포함)를
     * 직접 주입할 수 있도록 노출합니다.
     */
    KakaoOAuth2Handler(
            WebClient webClient,
            AccountRepository accountRepository,
            SocialAccountRepository socialAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider) {
        super(
                webClient,
                accountRepository,
                socialAccountRepository,
                refreshTokenRepository,
                jwtTokenProvider);
    }

    /**
     * Kakao userinfo API를 호출하여 사용자 정보를 조회합니다.
     *
     * @param providerAccessToken Kakao access token
     * @return 정규화된 {@link SocialUserInfo}
     */
    @Override
    protected Mono<SocialUserInfo> fetchUserInfo(String providerAccessToken) {
        return webClient.get()
                .uri(USERINFO_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + providerAccessToken)
                .retrieve()
                .bodyToMono(KakaoUserInfoResponse.class)
                .map(this::mapToSocialUserInfo);
    }

    /**
     * Kakao userinfo 응답을 {@link SocialUserInfo}로 변환합니다.
     *
     * @param response Kakao userinfo API 응답
     * @return 정규화된 소셜 사용자 정보
     */
    private SocialUserInfo mapToSocialUserInfo(KakaoUserInfoResponse response) {
        String email = null;
        String nickname = null;
        String profileImageUrl = null;
        if (response.kakaoAccount() != null) {
            email = response.kakaoAccount().email();
            KakaoUserInfoResponse.KakaoProfile profile = response.kakaoAccount().profile();
            if (profile != null) {
                nickname = profile.nickname();
                profileImageUrl = profile.profileImageUrl();
            }
        }
        return new SocialUserInfo(
                PROVIDER,
                String.valueOf(response.id()),
                email,
                nickname,
                profileImageUrl);
    }

    /**
     * Kakao userinfo API 최상위 응답 DTO.
     *
     * @param id           Kakao 고유 사용자 ID (숫자)
     * @param kakaoAccount 카카오 계정 상세 정보
     */
    record KakaoUserInfoResponse(
            @JsonProperty("id") long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

        /**
         * 카카오 계정 정보 DTO.
         *
         * @param email   이메일 주소 (nullable)
         * @param profile 프로필 정보 (nullable)
         */
        record KakaoAccount(
                @JsonProperty("email") String email,
                @JsonProperty("profile") KakaoProfile profile) {
        }

        /**
         * 카카오 프로필 정보 DTO.
         *
         * @param nickname        닉네임
         * @param profileImageUrl 프로필 이미지 URL
         */
        record KakaoProfile(
                @JsonProperty("nickname") String nickname,
                @JsonProperty("profile_image_url") String profileImageUrl) {
        }
    }
}
