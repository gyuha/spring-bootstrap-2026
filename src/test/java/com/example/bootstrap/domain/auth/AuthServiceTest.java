package com.example.bootstrap.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bootstrap.domain.auth.dto.LoginRequest;
import com.example.bootstrap.domain.auth.dto.RefreshRequest;
import com.example.bootstrap.domain.auth.dto.SignupRequest;
import com.example.bootstrap.domain.auth.service.AuthService;
import com.example.bootstrap.domain.auth.service.RefreshTokenStore;
import com.example.bootstrap.domain.user.entity.Role;
import com.example.bootstrap.domain.user.entity.User;
import com.example.bootstrap.domain.user.repository.UserRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.security.AuthProperties;
import com.example.bootstrap.global.security.JwtTokenProvider;
import com.example.bootstrap.global.security.TokenBlacklist;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AuthService 핵심 분기 단위 테스트 (RED→GREEN, TDD).
 *
 * <p>중복 가입(409), 자격 실패(401, 모호 메시지), Refresh 재사용(401) 분기를 mock으로 기술한다.
 * 전 흐름 통합 검증(가입→로그인→회전→재사용→로그아웃)은 02-04 통합 테스트가 담당한다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenStore refreshTokenStore;
    @Mock TokenBlacklist tokenBlacklist;
    @Mock AuthProperties authProperties;

    @InjectMocks AuthService authService;

    @Test
    void signup_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("dup@ex.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("dup@ex.com", "password1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_unknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmail("nobody@ex.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@ex.com", "password1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_wrongPassword_throwsInvalidCredentials() {
        User user = User.create("u@ex.com", "hashed");
        when(userRepository.findByEmail("u@ex.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("u@ex.com", "wrong")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void refresh_reuseDetected_throwsReuseDetected() {
        UUID userId = UUID.randomUUID();
        String familyId = UUID.randomUUID().toString();
        String refreshToken = userId + "." + familyId + ".some-secret";

        User user = User.create("u@ex.com", "hashed");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authProperties.refreshTtl()).thenReturn(Duration.ofDays(14));
        when(refreshTokenStore.rotate(any(UUID.class), anyString(), anyString(), anyString(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(refreshToken)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFRESH_REUSE_DETECTED);
    }

    @Test
    void refresh_malformedToken_throwsInvalidRefreshToken() {
        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("not-a-valid-token")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void logout_blacklistsJti() {
        UUID userId = UUID.randomUUID();
        var issued = new JwtTokenProvider.IssuedAccess("token", "jti-123", java.time.Instant.now().plusSeconds(60));
        // logout takes jti + exp directly
        authService.logout(issued.jti(), issued.exp());

        verify(tokenBlacklist).add("jti-123", issued.exp());
        // userId unused here; kept to mirror controller-extracted shape
        assertThat(userId).isNotNull();
    }
}
