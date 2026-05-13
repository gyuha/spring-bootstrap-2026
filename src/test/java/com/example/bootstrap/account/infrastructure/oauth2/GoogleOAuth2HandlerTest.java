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
 * {@link GoogleOAuth2Handler} 단위 테스트.
 *
 * <p>외부 Google HTTP 호출은 {@link ExchangeFunction}을 통해 Mock 처리됩니다.
 * 리포지토리는 Mockito로 대체됩니다.
 */
@ExtendWith(MockitoExtension.class)
class GoogleOAuth2HandlerTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-32-chars-long!!";
    private static final long ACCESS_EXPIRY_SEC = 1800L;
    private static final long REFRESH_EXPIRY_SEC = 1209600L;
    private static final String TEST_TOKEN = "google-access-token-for-test";

    @Mock
    private ExchangeFunction exchangeFunction;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private JwtTokenProvider jwtTokenProvider;
    private GoogleOAuth2Handler handler;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(TEST_SECRET, ACCESS_EXPIRY_SEC, REFRESH_EXPIRY_SEC);
        jwtTokenProvider = new JwtTokenProvider(props);

        WebClient webClient = WebClient.builder()
                .exchangeFunction(exchangeFunction)
                .build();

        handler = new GoogleOAuth2Handler(
                webClient,
                accountRepository,
                socialAccountRepository,
                refreshTokenRepository,
                jwtTokenProvider);
    }

    // ── fetchUserInfo — Google 응답 파싱 ──────────────────────────────────────

    @Test
    @DisplayName("fetchUserInfo: Google userinfo JSON을 올바르게 파싱하여 SocialUserInfo를 반환한다")
    void fetchUserInfo_validGoogleResponse_returnsParsedSocialUserInfo() {
        String json = "{\"sub\":\"google-sub-001\","
                + "\"email\":\"user@gmail.com\","
                + "\"name\":\"Test User\","
                + "\"picture\":\"https://lh3.googleusercontent.com/photo.jpg\"}";
        stubExchangeWithJson(json);

        // fetchUserInfo is protected but accessible from the same package
        Mono<SocialUserInfo> result = handler.fetchUserInfo(TEST_TOKEN);

        StepVerifier.create(result)
                .assertNext(info -> {
                    assertThat(info.provider()).isEqualTo("google");
                    assertThat(info.providerId()).isEqualTo("google-sub-001");
                    assertThat(info.email()).isEqualTo("user@gmail.com");
                    assertThat(info.nickname()).isEqualTo("Test User");
                    assertThat(info.profileImageUrl())
                            .isEqualTo("https://lh3.googleusercontent.com/photo.jpg");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchUserInfo: picture 필드가 null인 경우에도 정상 파싱된다")
    void fetchUserInfo_nullPicture_parsedWithNullProfileImageUrl() {
        String json = "{\"sub\":\"google-sub-002\","
                + "\"email\":\"user2@gmail.com\","
                + "\"name\":\"No Photo\","
                + "\"picture\":null}";
        stubExchangeWithJson(json);

        StepVerifier.create(handler.fetchUserInfo(TEST_TOKEN))
                .assertNext(info -> {
                    assertThat(info.providerId()).isEqualTo("google-sub-002");
                    assertThat(info.profileImageUrl()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("fetchUserInfo: Authorization 헤더에 'Bearer <token>'이 포함된다")
    void fetchUserInfo_requestContainsBearerAuthorizationHeader() {
        String json = "{\"sub\":\"g-001\",\"email\":\"u@g.com\",\"name\":\"U\",\"picture\":null}";
        stubExchangeWithJson(json);

        handler.fetchUserInfo(TEST_TOKEN).block();

        verify(exchangeFunction).exchange(
                org.mockito.ArgumentMatchers.argThat(req ->
                        ("Bearer " + TEST_TOKEN).equals(
                                req.headers().getFirst(HttpHeaders.AUTHORIZATION))));
    }

    @Test
    @DisplayName("fetchUserInfo: 요청 URL이 Google userinfo 엔드포인트를 대상으로 한다")
    void fetchUserInfo_requestTargetsGoogleUserinfoEndpoint() {
        String json = "{\"sub\":\"g-001\",\"email\":\"u@g.com\",\"name\":\"U\",\"picture\":null}";
        stubExchangeWithJson(json);

        handler.fetchUserInfo(TEST_TOKEN).block();

        verify(exchangeFunction).exchange(
                org.mockito.ArgumentMatchers.argThat(req ->
                        GoogleOAuth2Handler.USERINFO_URL.equals(req.url().toString())));
    }

    // ── authenticate — 신규 계정 흐름 ────────────────────────────────────────

    @Test
    @DisplayName("authenticate: 신규 사용자 — Account와 SocialAccount가 생성된다")
    void authenticate_newUser_createsAccountAndSocialAccount() {
        stubExchangeWithJson(newUserGoogleJson("new-google-id", "new@gmail.com", "New User"));

        Account savedAccount = buildAccount(10L, "new@gmail.com", "New User");
        when(socialAccountRepository.findByProviderAndProviderId("google", "new-google-id"))
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
    @DisplayName("authenticate: 신규 사용자 — 저장되는 Account에 이메일이 올바르게 설정된다")
    void authenticate_newUser_accountSavedWithCorrectEmail() {
        stubExchangeWithJson(newUserGoogleJson("g-email-test", "correct@gmail.com", "Email Test"));

        Account savedAccount = buildAccount(20L, "correct@gmail.com", "Email Test");
        when(socialAccountRepository.findByProviderAndProviderId("google", "g-email-test"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(2L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).isEqualTo("correct@gmail.com");
    }

    @Test
    @DisplayName("authenticate: 신규 사용자 — role이 USER이고 emailVerified가 true이다")
    void authenticate_newUser_accountSavedWithUserRoleAndEmailVerified() {
        stubExchangeWithJson(newUserGoogleJson("g-role-test", "role@gmail.com", "Role Test"));

        Account savedAccount = buildAccount(30L, "role@gmail.com", "Role Test");
        when(socialAccountRepository.findByProviderAndProviderId("google", "g-role-test"))
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

    @Test
    @DisplayName("authenticate: 신규 사용자 — SocialAccount에 provider와 providerId가 올바르게 저장된다")
    void authenticate_newUser_socialAccountSavedWithCorrectProviderInfo() {
        stubExchangeWithJson(newUserGoogleJson("g-social-test", "social@gmail.com", "Social Test"));

        Account savedAccount = buildAccount(40L, "social@gmail.com", "Social Test");
        when(socialAccountRepository.findByProviderAndProviderId("google", "g-social-test"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(4L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<SocialAccount> captor = ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository).save(captor.capture());
        assertThat(captor.getValue().getProvider()).isEqualTo("google");
        assertThat(captor.getValue().getProviderId()).isEqualTo("g-social-test");
        assertThat(captor.getValue().getUserId()).isEqualTo(40L);
    }

    // ── authenticate — 기존 계정 흐름 ────────────────────────────────────────

    @Test
    @DisplayName("authenticate: 기존 사용자 — Account와 SocialAccount 생성 없이 기존 계정을 사용한다")
    void authenticate_existingUser_usesExistingAccountWithoutCreating() {
        stubExchangeWithJson(newUserGoogleJson("existing-google-id", "existing@gmail.com", "Existing"));

        SocialAccount existingSa = buildSocialAccount(1L, 50L, "google", "existing-google-id");
        Account existingAccount = buildAccount(50L, "existing@gmail.com", "Existing");
        when(socialAccountRepository.findByProviderAndProviderId("google", "existing-google-id"))
                .thenReturn(Mono.just(existingSa));
        when(accountRepository.findById(any(Long.class))).thenReturn(Mono.just(existingAccount));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(5L)));

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
        stubExchangeWithJson(newUserGoogleJson("lookup-google-id", "lookup@gmail.com", "Lookup"));

        SocialAccount existingSa = buildSocialAccount(2L, 99L, "google", "lookup-google-id");
        Account existingAccount = buildAccount(99L, "lookup@gmail.com", "Lookup");
        when(socialAccountRepository.findByProviderAndProviderId("google", "lookup-google-id"))
                .thenReturn(Mono.just(existingSa));
        when(accountRepository.findById(any(Long.class))).thenReturn(Mono.just(existingAccount));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(6L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(accountRepository).findById(idCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(99L);
    }

    // ── authenticate — 토큰 발급 흐름 ────────────────────────────────────────

    @Test
    @DisplayName("authenticate: 발급된 Access Token은 유효한 JWT이며 올바른 userId(sub)를 포함한다")
    void authenticate_issuedAccessToken_isValidJwtWithCorrectUserId() {
        stubExchangeWithJson(newUserGoogleJson("g-jwt-test", "jwt@gmail.com", "JWT Test"));

        Account savedAccount = buildAccount(77L, "jwt@gmail.com", "JWT Test");
        when(socialAccountRepository.findByProviderAndProviderId("google", "g-jwt-test"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(7L)));

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
        stubExchangeWithJson(newUserGoogleJson("g-refresh-test", "refresh@gmail.com", "Refresh"));

        Account savedAccount = buildAccount(88L, "refresh@gmail.com", "Refresh");
        when(socialAccountRepository.findByProviderAndProviderId("google", "g-refresh-test"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(8L)));

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
        stubExchangeWithJson(newUserGoogleJson("g-save-test", "save@gmail.com", "Save Test"));

        Account savedAccount = buildAccount(55L, "save@gmail.com", "Save Test");
        when(socialAccountRepository.findByProviderAndProviderId("google", "g-save-test"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(9L)));

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
    @DisplayName("authenticate: email이 없는 경우 placeholder 이메일로 Account가 생성된다")
    void authenticate_noEmail_createsAccountWithPlaceholderEmail() {
        String json = "{\"sub\":\"g-no-email\","
                + "\"email\":null,"
                + "\"name\":\"No Email User\","
                + "\"picture\":null}";
        stubExchangeWithJson(json);

        Account savedAccount = buildAccount(
                66L, "google_g-no-email@social.placeholder", "No Email User");
        when(socialAccountRepository.findByProviderAndProviderId("google", "g-no-email"))
                .thenReturn(Mono.empty());
        when(accountRepository.save(any())).thenReturn(Mono.just(savedAccount));
        when(socialAccountRepository.save(any())).thenReturn(Mono.just(new SocialAccount()));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedRefreshToken(10L)));

        StepVerifier.create(handler.authenticate(TEST_TOKEN))
                .assertNext(response -> assertThat(response.accessToken()).isNotBlank())
                .verifyComplete();

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getEmail()).contains("google_g-no-email");
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    private void stubExchangeWithJson(String json) {
        ClientResponse response = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
        when(exchangeFunction.exchange(any())).thenReturn(Mono.just(response));
    }

    private static String newUserGoogleJson(String sub, String email, String name) {
        return "{\"sub\":\"" + sub + "\","
                + "\"email\":\"" + email + "\","
                + "\"name\":\"" + name + "\","
                + "\"picture\":null}";
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
