package com.example.bootstrap.account.infrastructure.oauth2;

import com.example.bootstrap.account.domain.model.Account;
import com.example.bootstrap.account.domain.model.RefreshToken;
import com.example.bootstrap.account.domain.model.SocialAccount;
import com.example.bootstrap.account.domain.repository.AccountRepository;
import com.example.bootstrap.account.domain.repository.RefreshTokenRepository;
import com.example.bootstrap.account.domain.repository.SocialAccountRepository;
import com.example.bootstrap.global.security.jwt.JwtProperties;
import com.example.bootstrap.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KakaoOAuth2Handler} 단위 테스트.
 *
 * <p>외부 Kakao HTTP 호출은 {@link ExchangeFunction}을 통해 Mock 처리됩니다.
 * 리포지토리는 Mockito로 대체됩니다.
 */
@ExtendWith(MockitoExtension.class)
class KakaoOAuth2HandlerTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-32-chars-long!!";
    private static final long ACCESS_EXPIRY_SEC = 1800L;
    private static final long REFRESH_EXPIRY_SEC = 1209600L;
    private static final String TEST_TOKEN = "kakao-access-token-for-test";

    @Mock
    private ExchangeFunction exchangeFunction;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenProvider jwtTokenProvider;
    private KakaoOAuth2Handler handler;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(TEST_SECRET, ACCESS_EXPIRY_SEC, REFRESH_EXPIRY_SEC);
        jwtTokenProvider = new JwtTokenProvider(props);

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        handler = new KakaoOAuth2Handler(
                webClient,
                accountRepository,
                socialAccountRepository,
                refreshTokenRepository,
                jwtTokenProvider);
    }

    // ── fetchUserInfo — Kakao 응답 파싱 ──────────────────────────────────────

    @Test
    @DisplayName("fetchUserInfo: Kakao userinfo JSON을 올바르게 파싱하여 SocialUserInfo를 반환한다")
    void fetchUserInfo_validKakaoResponse_returnsParsedSocialUserInfo() {
        String json = "{"
                + "\"id\":12345678,"
                + "\"kakao_account\":{"
                + "\"email\":\"user@kakao.com\","
                + "\"profile\":{"
                + "\"nickname\":\"카카오유저\","
                + "\"profile_image_url\":\"https://k.kakaocdn.net/photo.jpg\""
                + "}}}";
        stubExchangeWithJson(json);

        StepVerifier.create(handler.fetchUserInfo(TEST_TOKEN))
                .assertNext(info -> {
                    assertThat(info.provider()).isEqualTo("kakao");
                    assertThat(info.providerId()).isEqualTo("12345678");
                    assertThat(info.email()).isEqualTo("user@kakao.com");
                    assertThat(info.nickname()).isEqualTo("카카오유저");
                    assertThat(info.profileImageUrl())
                            .isEqualTo("https://k.kakaocdn.net/photo.jpg");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchUserInfo: Kakao id는 숫자이지만 providerId는 문자열로 반환된다")
    void fetchUserInfo_kakaoNumericId_convertedToStringProviderId() {
        String json = "{"
                + "\"id\":99999999,"
                + "\"kakao_account\":{"
                + "\"email\":\"num@kakao.com\","
                + "\"profile\":{\"nickname\":\"NumberUser\",\"profile_image_url\":null}"
                + "}}";
        stubExchangeWithJson(json);

        StepVerifier.create(handler.fetchUserInfo(TEST_TOKEN))
                .assertNext(info -> {
                    assertThat(info.providerId()).isEqualTo("99999999");
                    assertThat(info.providerId()).isInstanceOf(String.class);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchUserInfo: kakao_account가 null이면 email/nickname/profileImageUrl이 null이다")
    void fetchUserInfo_nullKakaoAccount_returnsNullEmailAndNickname() {
        String json = "{\"id\":77777777,\"kakao_account\":null}";
        stubExchangeWithJson(json);

        StepVerifier.create(handler.fetchUserInfo(TEST_TOKEN))
                .assertNext(info -> {
                    assertThat(info.provider()).isEqualTo("kakao");
                    assertThat(info.providerId()).isEqualTo("77777777");
                    assertThat(info.email()).isNull();
                    assertThat(info.nickname()).isNull();
                    assertThat(info.profileImageUrl()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchUserInfo: profile이 null이면 nickname/profileImageUrl이 null이다")
    void fetchUserInfo_nullProfile_returnsNullNicknameAndProfileImageUrl() {
        String json = "{"
                + "\"id\":55555555,"
                + "\"kakao_account\":{"
                + "\"email\":\"noProfile@kakao.com\","
                + "\"profile\":null"
                + "}}";
        stubExchangeWithJson(json);

        StepVerifier.create(handler.fetchUserInfo(TEST_TOKEN))
                .assertNext(info -> {
                    assertThat(info.email()).isEqualTo("noProfile@kakao.com");
                    assertThat(info.nickname()).isNull();
                    assertThat(info.profileImageUrl()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchUserInfo: Authorization 헤더에 'Bearer <token>'이 포함된다")
    void fetchUserInfo_requestContainsBearerAuthorizationHeader() {
        stubExchangeWithJson(basicKakaoJson(11111L));

        handler.fetchUserInfo(TEST_TOKEN).block();

        verify(exchangeFunction).exchange(
                org.mockito.ArgumentMatchers.argThat(req ->
                        ("Bearer " + TEST_TOKEN).equals(
                                req.headers().getFirst(HttpHeaders.AUTHORIZATION))));
    }

    @Test
    @DisplayName("fetchUserInfo: 요청 URL이 Kakao userinfo 엔드포인트를 대상으로 한다")
    void fetchUserInfo_requestTargetsKakaoUserinfoEndpoint() {
        stubExchangeWithJson(basicKakaoJson(22222L));

        handler.fetchUserInfo(TEST_TOKEN).block();

        verify(exchangeFunction).exchange(
                org.mockito.ArgumentMatchers.argThat(req ->
                        KakaoOAuth2Handler.USERINFO_URL.equals(req.url().toString())));
    }

    // ── authenticate — 신규 계정 흐름 ────────────────────────────────────────

    @Test
    @DisplayName("authenticate: 신규 사용자 — Account와 SocialAccount가 생성된다")
    void authenticate_newUser_createsAccountAndSocialAccount() {
        String json = fullKakaoJson(33333L, "new@kakao.com", "신규유저", null);
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(10L, "new@kakao.com", "신규유저");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "33333"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(1L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> {
                    assertThat(response.accessToken()).isNotBlank();
                    assertThat(response.refreshToken()).isNotBlank();
                })
                .verifyComplete();

        verify(accountRepository).save(any(Account.class));
        verify(socialAccountRepository).save(any(SocialAccount.class));
    }

    @Test
    @DisplayName("authenticate: 신규 사용자 — SocialAccount의 provider가 'kakao'이다")
    void authenticate_newUser_socialAccountHasKakaoProvider() {
        String json = fullKakaoJson(44444L, "kakao@kakao.com", "카카오테스트", null);
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(20L, "kakao@kakao.com", "카카오테스트");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "44444"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(2L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("kakao");
        assertThat(captor.getValue().getProviderId()).isEqualTo("44444");
        assertThat(captor.getValue().getUserId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("authenticate: 신규 사용자 — role이 USER이고 emailVerified가 true이다")
    void authenticate_newUser_accountHasUserRoleAndEmailVerifiedTrue() {
        String json = fullKakaoJson(55555L, "role@kakao.com", "역할테스트", null);
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(30L, "role@kakao.com", "역할테스트");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "55555"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(3L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getRole()).isEqualTo("USER");
        assertThat(accountCaptor.getValue().isEmailVerified()).isTrue();
    }

    // ── authenticate — 기존 계정 흐름 ────────────────────────────────────────

    @Test
    @DisplayName("authenticate: 기존 사용자 — Account와 SocialAccount 생성 없이 기존 계정을 사용한다")
    void authenticate_existingUser_usesExistingAccountWithoutCreating() {
        String json = fullKakaoJson(66666L, "existing@kakao.com", "기존유저", null);
        stubExchangeWithJson(json);

        SocialAccount existingSa = buildSocialAccount(1L, 50L, "kakao", "66666");
        Account existingAccount = buildAccount(50L, "existing@kakao.com", "기존유저");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "66666"))
                .thenReturn(Mono.just(existingSa));
        when(accountRepository.findById(any(Long.class))).thenReturn(Mono.just(existingAccount));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(4L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> {
                    assertThat(response.accessToken()).isNotBlank();
                    assertThat(response.refreshToken()).isNotBlank();
                })
                .verifyComplete();

        verify(accountRepository, never()).save(any());
        verify(socialAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("authenticate: 기존 사용자 — SocialAccount의 userId로 Account를 조회한다")
    void authenticate_existingUser_findsAccountBySocialAccountUserId() {
        String json = fullKakaoJson(77777L, "lookup@kakao.com", "조회테스트", null);
        stubExchangeWithJson(json);

        SocialAccount existingSa = buildSocialAccount(2L, 123L, "kakao", "77777");
        Account existingAccount = buildAccount(123L, "lookup@kakao.com", "조회테스트");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "77777"))
                .thenReturn(Mono.just(existingSa));
        when(accountRepository.findById(any(Long.class))).thenReturn(Mono.just(existingAccount));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(5L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(accountRepository).findById(idCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(123L);
    }

    // ── authenticate — 토큰 발급 흐름 ────────────────────────────────────────

    @Test
    @DisplayName("authenticate: 발급된 Access Token은 유효한 JWT이며 올바른 userId(sub)를 포함한다")
    void authenticate_issuedAccessToken_isValidJwtWithCorrectUserId() {
        String json = fullKakaoJson(88888L, "jwt@kakao.com", "JWT테스트", null);
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(77L, "jwt@kakao.com", "JWT테스트");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "88888"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(6L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> {
                    assertThat(jwtTokenProvider.isValid(response.accessToken())).isTrue();
                    assertThat(jwtTokenProvider.extractUserId(response.accessToken())).isEqualTo(77L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticate: 발급된 Refresh Token은 유효한 JWT이며 올바른 userId(sub)를 포함한다")
    void authenticate_issuedRefreshToken_isValidJwtWithCorrectUserId() {
        String json = fullKakaoJson(99999L, "refresh@kakao.com", "갱신테스트", null);
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(88L, "refresh@kakao.com", "갱신테스트");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "99999"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(7L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> {
                    assertThat(jwtTokenProvider.isValid(response.refreshToken())).isTrue();
                    assertThat(jwtTokenProvider.extractUserId(response.refreshToken())).isEqualTo(88L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticate: Refresh Token이 올바른 userId와 expiredAt을 포함하여 DB에 저장된다")
    void authenticate_refreshToken_savedToDatabaseWithCorrectFields() {
        String json = fullKakaoJson(11111L, "save@kakao.com", "저장테스트", null);
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(55L, "save@kakao.com", "저장테스트");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "11111"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(8L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.refreshToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        RefreshToken captured = tokenCaptor.getValue();
        assertThat(captured.getUserId()).isEqualTo(55L);
        assertThat(captured.getToken()).isNotBlank();
        assertThat(captured.getExpiredAt()).isNotNull();
    }

    @Test
    @DisplayName("authenticate: Kakao email 없이 profileImageUrl만 있는 경우 placeholder 이메일로 계정이 생성된다")
    void authenticate_noEmail_createsAccountWithPlaceholderEmail() {
        String json = "{"
                + "\"id\":22222,"
                + "\"kakao_account\":{"
                + "\"email\":null,"
                + "\"profile\":{\"nickname\":\"NoEmail\",\"profile_image_url\":null}"
                + "}}";
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(66L, "kakao_22222@social.placeholder", "NoEmail");
        when(socialAccountRepository.findByProviderAndProviderId("kakao", "22222"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(9L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).contains("kakao_22222");
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    private void stubExchangeWithJson(String json) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));
    }

    private static String basicKakaoJson(long id) {
        return "{\"id\":" + id + ",\"kakao_account\":"
                + "{\"email\":\"user@kakao.com\","
                + "\"profile\":{\"nickname\":\"User\",\"profile_image_url\":null}}}";
    }

    private static String fullKakaoJson(long id, String email, String nickname, String imageUrl) {
        String imageJson = imageUrl == null ? "null" : "\"" + imageUrl + "\"";
        return "{\"id\":" + id + ",\"kakao_account\":"
                + "{\"email\":\"" + email + "\","
                + "\"profile\":{\"nickname\":\"" + nickname + "\","
                + "\"profile_image_url\":" + imageJson + "}}}";
    }

    private static Account buildAccount(Long id, String email, String nickname) {
        Account account = new Account();
        account.setId(id);
        account.setEmail(email);
        account.setNickname(nickname);
        account.setRole("USER");
        account.setEmailVerified(true);
        return account;
    }

    private static SocialAccount buildSocialAccount(
            Long id, Long userId, String provider, String providerId) {
        SocialAccount sa = new SocialAccount();
        sa.setId(id);
        sa.setUserId(userId);
        sa.setProvider(provider);
        sa.setProviderId(providerId);
        return sa;
    }

    private static RefreshToken savedRefreshToken(Long id) {
        RefreshToken token = new RefreshToken();
        token.setId(id);
        return token;
    }
}
