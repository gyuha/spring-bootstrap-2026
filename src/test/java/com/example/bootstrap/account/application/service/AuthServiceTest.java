package com.example.bootstrap.account.application.service;

import com.example.bootstrap.account.application.dto.LoginRequest;
import com.example.bootstrap.account.application.dto.TokenResponse;
import com.example.bootstrap.account.domain.model.Account;
import com.example.bootstrap.account.domain.model.RefreshToken;
import com.example.bootstrap.account.domain.repository.AccountRepository;
import com.example.bootstrap.account.domain.repository.RefreshTokenRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.security.jwt.JwtBlacklistService;
import com.example.bootstrap.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "plain";
    private static final String ENCODED = "$2a$10$hash";
    private static final String ACCESS_TOKEN = "access.token.value";
    private static final String REFRESH_TOKEN = "refresh.token.value";

    @Mock private AccountRepository accountRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtBlacklistService jwtBlacklistService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                accountRepository, refreshTokenRepository,
                passwordEncoder, jwtTokenProvider, jwtBlacklistService);
    }

    // ── login ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: 이메일과 비밀번호가 올바르면 TokenResponse를 반환한다")
    void login_withValidCredentials_returnsTokenResponse() {
        Account account = buildAccount(1L, EMAIL, ENCODED);
        RefreshToken savedToken = buildRefreshToken(1L, 1L, REFRESH_TOKEN);

        when(accountRepository.findByEmail(EMAIL)).thenReturn(Mono.just(account));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(true);
        when(jwtTokenProvider.generateAccessToken(1L, EMAIL, "USER")).thenReturn(ACCESS_TOKEN);
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn(REFRESH_TOKEN);
        when(jwtTokenProvider.extractExpiresAt(REFRESH_TOKEN))
                .thenReturn(LocalDateTime.now().plusDays(14));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedToken));

        StepVerifier.create(authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .assertNext(response -> {
                    assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
                    assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("login: 이메일이 없으면 BusinessException(AUTH_004)을 발생시킨다")
    void login_whenEmailNotFound_throwsAuth004() {
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Mono.empty());

        StepVerifier.create(authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_004);
                })
                .verify();

        verify(jwtTokenProvider, never()).generateAccessToken(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("login: 비밀번호가 틀리면 BusinessException(AUTH_004)을 발생시킨다")
    void login_whenPasswordMismatch_throwsAuth004() {
        Account account = buildAccount(1L, EMAIL, ENCODED);

        when(accountRepository.findByEmail(EMAIL)).thenReturn(Mono.just(account));
        when(passwordEncoder.matches(PASSWORD, ENCODED)).thenReturn(false);

        StepVerifier.create(authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_004);
                })
                .verify();
    }

    @Test
    @DisplayName("login: OAuth2 전용 계정(password=null)은 BusinessException(AUTH_004)을 발생시킨다")
    void login_whenOAuth2OnlyAccount_throwsAuth004() {
        Account oauthAccount = buildAccount(2L, EMAIL, null);

        when(accountRepository.findByEmail(EMAIL)).thenReturn(Mono.just(oauthAccount));

        StepVerifier.create(authService.login(new LoginRequest(EMAIL, PASSWORD)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_004);
                })
                .verify();
    }

    // ── refresh ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("refresh: 유효한 Refresh Token이면 새 TokenResponse를 반환한다")
    void refresh_withValidToken_returnsNewTokenResponse() {
        RefreshToken tokenEntity = buildRefreshToken(10L, 1L, REFRESH_TOKEN);
        Account account = buildAccount(1L, EMAIL, ENCODED);
        RefreshToken savedToken = buildRefreshToken(11L, 1L, "new.refresh.token");

        when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
        when(jwtBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(Mono.just(false));
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN)).thenReturn(Mono.just(tokenEntity));
        when(refreshTokenRepository.deleteByUserId(1L)).thenReturn(Mono.<Void>empty());
        when(accountRepository.findById(1L)).thenReturn(Mono.just(account));
        when(jwtTokenProvider.generateAccessToken(1L, EMAIL, "USER")).thenReturn("new.access.token");
        when(jwtTokenProvider.generateRefreshToken(1L)).thenReturn("new.refresh.token");
        when(jwtTokenProvider.extractExpiresAt("new.refresh.token"))
                .thenReturn(LocalDateTime.now().plusDays(14));
        when(refreshTokenRepository.save(any())).thenReturn(Mono.just(savedToken));

        StepVerifier.create(authService.refresh(REFRESH_TOKEN))
                .assertNext(response -> {
                    assertThat(response.accessToken()).isEqualTo("new.access.token");
                    assertThat(response.refreshToken()).isEqualTo("new.refresh.token");
                })
                .verifyComplete();

        verify(refreshTokenRepository).deleteByUserId(1L);
    }

    @Test
    @DisplayName("refresh: 만료된 토큰이면 BusinessException(AUTH_002)을 발생시킨다")
    void refresh_whenTokenExpired_throwsAuth002() {
        when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(false);

        StepVerifier.create(authService.refresh(REFRESH_TOKEN))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_002);
                })
                .verify();
    }

    @Test
    @DisplayName("refresh: 블랙리스트 토큰이면 BusinessException(AUTH_003)을 발생시킨다")
    void refresh_whenTokenBlacklisted_throwsAuth003() {
        when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
        when(jwtBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(Mono.just(true));

        StepVerifier.create(authService.refresh(REFRESH_TOKEN))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_003);
                })
                .verify();
    }

    @Test
    @DisplayName("refresh: DB에 없는 토큰이면 BusinessException(AUTH_005)을 발생시킨다 — 재사용 감지")
    void refresh_whenTokenNotInDb_throwsAuth005() {
        when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
        when(jwtBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(Mono.just(false));
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(authService.refresh(REFRESH_TOKEN))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.AUTH_005);
                })
                .verify();
    }

    @Test
    @DisplayName("refresh: Refresh Token은 유효하지만 해당 userId의 계정이 없으면 BusinessException(ACCOUNT_002)을 발생시킨다")
    void refresh_whenAccountNotFound_throwsAccount002() {
        RefreshToken tokenEntity = buildRefreshToken(10L, 99L, REFRESH_TOKEN);

        when(jwtTokenProvider.isValid(REFRESH_TOKEN)).thenReturn(true);
        when(jwtBlacklistService.isBlacklisted(REFRESH_TOKEN)).thenReturn(Mono.just(false));
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN)).thenReturn(Mono.just(tokenEntity));
        when(refreshTokenRepository.deleteByUserId(99L)).thenReturn(Mono.<Void>empty());
        when(accountRepository.findById(99L)).thenReturn(Mono.empty());

        StepVerifier.create(authService.refresh(REFRESH_TOKEN))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_002);
                })
                .verify();
    }

    // ── logout ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("logout: Access Token을 블랙리스트에 등록하고 Refresh Token을 삭제한다")
    void logout_blacklistsAccessTokenAndDeletesRefreshToken() {
        RefreshToken tokenEntity = buildRefreshToken(20L, 1L, REFRESH_TOKEN);

        when(jwtBlacklistService.addToBlacklist(ACCESS_TOKEN)).thenReturn(Mono.just(true));
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN)).thenReturn(Mono.just(tokenEntity));
        when(refreshTokenRepository.delete(tokenEntity)).thenReturn(Mono.<Void>empty());

        StepVerifier.create(authService.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .verifyComplete();

        verify(jwtBlacklistService).addToBlacklist(ACCESS_TOKEN);
        verify(refreshTokenRepository).delete(tokenEntity);
    }

    @Test
    @DisplayName("logout: Refresh Token이 DB에 없어도 정상 완료된다 (멱등)")
    void logout_whenRefreshTokenNotFound_completesSuccessfully() {
        when(jwtBlacklistService.addToBlacklist(ACCESS_TOKEN)).thenReturn(Mono.just(true));
        when(refreshTokenRepository.findByToken(REFRESH_TOKEN)).thenReturn(Mono.empty());

        StepVerifier.create(authService.logout(ACCESS_TOKEN, REFRESH_TOKEN))
                .verifyComplete();
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static Account buildAccount(Long id, String email, String password) {
        Account a = new Account();
        a.setId(id);
        a.setEmail(email);
        a.setPassword(password);
        a.setNickname("User");
        a.setRole("USER");
        a.setEmailVerified(true);
        return a;
    }

    private static RefreshToken buildRefreshToken(Long id, Long userId, String token) {
        RefreshToken rt = new RefreshToken();
        rt.setId(id);
        rt.setUserId(userId);
        rt.setToken(token);
        rt.setExpiredAt(LocalDateTime.now().plusDays(14));
        return rt;
    }
}
