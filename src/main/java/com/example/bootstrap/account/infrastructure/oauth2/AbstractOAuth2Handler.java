package com.example.bootstrap.account.infrastructure.oauth2;

import com.example.bootstrap.account.domain.model.Account;
import com.example.bootstrap.account.domain.model.RefreshToken;
import com.example.bootstrap.account.domain.model.SocialAccount;
import com.example.bootstrap.account.domain.repository.AccountRepository;
import com.example.bootstrap.account.domain.repository.RefreshTokenRepository;
import com.example.bootstrap.account.domain.repository.SocialAccountRepository;
import com.example.bootstrap.global.security.jwt.JwtTokenProvider;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OAuth2 소셜 로그인 공통 흐름을 처리하는 추상 핸들러.
 *
 * <p>공통 흐름: provider userinfo 조회 → 신규/기존 계정 분기 처리 → JWT 토큰 발급.
 * 각 provider별 구현체는 {@link #fetchUserInfo(String)}만 재정의합니다.
 */
public abstract class AbstractOAuth2Handler {

    private static final String DEFAULT_ROLE = "USER";

    private final AccountRepository accountRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /** provider userinfo API 호출에 사용되는 WebClient. */
    protected final WebClient webClient;

    /**
     * 추상 핸들러 생성자.
     *
     * @param webClient              provider userinfo API WebClient
     * @param accountRepository      계정 리포지토리
     * @param socialAccountRepository 소셜 계정 리포지토리
     * @param refreshTokenRepository  Refresh Token 리포지토리
     * @param jwtTokenProvider        JWT 토큰 생성/파싱 유틸
     */
    protected AbstractOAuth2Handler(
            WebClient webClient,
            AccountRepository accountRepository,
            SocialAccountRepository socialAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenProvider jwtTokenProvider) {
        this.webClient = webClient;
        this.accountRepository = accountRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * OAuth2 소셜 로그인 전체 흐름을 실행합니다.
     *
     * <p>provider access token을 사용해 userinfo를 조회하고,
     * 신규 계정은 생성, 기존 계정은 조회 후 JWT 토큰을 발급합니다.
     *
     * @param providerAccessToken OAuth2 provider access token (프론트에서 전달)
     * @return 발급된 {@link AuthTokenResponse}
     */
    public Mono<AuthTokenResponse> authenticate(String providerAccessToken) {
        return fetchUserInfo(providerAccessToken)
                .flatMap(this::findOrCreateAccount)
                .flatMap(this::issueTokens);
    }

    /**
     * OAuth2 provider userinfo API를 호출하여 사용자 정보를 조회합니다.
     *
     * <p>각 provider 구현체에서 재정의합니다.
     *
     * @param providerAccessToken OAuth2 provider access token
     * @return 정규화된 {@link SocialUserInfo}
     */
    protected abstract Mono<SocialUserInfo> fetchUserInfo(String providerAccessToken);

    /**
     * 소셜 계정 기반으로 기존 계정을 조회하거나 신규 계정을 생성합니다.
     *
     * <p>{@code Mono.defer}를 사용하여 신규 계정 생성 로직을 업스트림이 비어 있을 때만
     * 지연 평가합니다. {@code switchIfEmpty(createNewAccount(...))} 형태로 사용하면
     * Java가 인수를 즉시 평가하여 항상 DB 저장이 호출되는 문제가 발생합니다.
     *
     * @param userInfo provider에서 수신한 사용자 정보
     * @return 조회 또는 생성된 {@link Account}
     */
    private Mono<Account> findOrCreateAccount(SocialUserInfo userInfo) {
        return socialAccountRepository
                .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                .flatMap(socialAccount -> accountRepository.findById(socialAccount.getUserId()))
                .switchIfEmpty(Mono.defer(() -> createNewAccount(userInfo)));
    }

    /**
     * 신규 계정과 소셜 계정 연동 레코드를 생성합니다.
     *
     * @param userInfo provider에서 수신한 사용자 정보
     * @return 생성된 {@link Account}
     */
    private Mono<Account> createNewAccount(SocialUserInfo userInfo) {
        Account account = buildAccount(userInfo);
        return accountRepository.save(account)
                .flatMap(savedAccount -> {
                    SocialAccount socialAccount = buildSocialAccount(savedAccount.getId(), userInfo);
                    return socialAccountRepository.save(socialAccount)
                            .thenReturn(savedAccount);
                });
    }

    /**
     * userInfo를 기반으로 Account 엔티티를 초기화합니다.
     *
     * @param userInfo provider 사용자 정보
     * @return 초기화된 {@link Account}
     */
    private Account buildAccount(SocialUserInfo userInfo) {
        Account account = new Account();
        String email = resolveEmail(userInfo);
        account.setEmail(email);
        account.setNickname(resolveNickname(userInfo));
        account.setRole(DEFAULT_ROLE);
        account.setEmailVerified(true);
        account.setProfileImageUrl(userInfo.profileImageUrl());
        return account;
    }

    /**
     * 이메일을 결정합니다. provider가 이메일을 미제공하면 placeholder를 사용합니다.
     *
     * @param userInfo provider 사용자 정보
     * @return 이메일 문자열
     */
    private String resolveEmail(SocialUserInfo userInfo) {
        if (userInfo.email() != null && !userInfo.email().isBlank()) {
            return userInfo.email();
        }
        return userInfo.provider() + "_" + userInfo.providerId() + "@social.placeholder";
    }

    /**
     * 닉네임을 결정합니다. provider가 닉네임을 미제공하면 "User"를 사용합니다.
     *
     * @param userInfo provider 사용자 정보
     * @return 닉네임 문자열
     */
    private String resolveNickname(SocialUserInfo userInfo) {
        if (userInfo.nickname() != null && !userInfo.nickname().isBlank()) {
            return userInfo.nickname();
        }
        return "User";
    }

    /**
     * SocialAccount 엔티티를 초기화합니다.
     *
     * @param accountId 연결할 Account PK
     * @param userInfo  provider 사용자 정보
     * @return 초기화된 {@link SocialAccount}
     */
    private SocialAccount buildSocialAccount(Long accountId, SocialUserInfo userInfo) {
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.setUserId(accountId);
        socialAccount.setProvider(userInfo.provider());
        socialAccount.setProviderId(userInfo.providerId());
        return socialAccount;
    }

    /**
     * 계정에 대한 JWT Access Token 및 Refresh Token을 발급합니다.
     *
     * <p>Refresh Token은 {@code refresh_tokens} 테이블에 저장됩니다.
     *
     * @param account 토큰을 발급할 계정
     * @return 발급된 {@link AuthTokenResponse}
     */
    private Mono<AuthTokenResponse> issueTokens(Account account) {
        String refreshToken = jwtTokenProvider.generateRefreshToken(account.getId());

        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setUserId(account.getId());
        tokenEntity.setToken(refreshToken);
        tokenEntity.setExpiredAt(jwtTokenProvider.extractExpiresAt(refreshToken));

        String accessToken = jwtTokenProvider
                .generateAccessToken(account.getId(), account.getEmail(), account.getRole());
        return refreshTokenRepository.save(tokenEntity)
                .thenReturn(new AuthTokenResponse(accessToken, refreshToken));
    }
}
