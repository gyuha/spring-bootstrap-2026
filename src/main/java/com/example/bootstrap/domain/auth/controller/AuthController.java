package com.example.bootstrap.domain.auth.controller;

import com.example.bootstrap.domain.auth.dto.LoginRequest;
import com.example.bootstrap.domain.auth.dto.RefreshRequest;
import com.example.bootstrap.domain.auth.dto.SignupRequest;
import com.example.bootstrap.domain.auth.dto.TokenResponse;
import com.example.bootstrap.domain.auth.service.AuthService;
import com.example.bootstrap.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 엔드포인트 — 가입/로그인/재발급/로그아웃 (AUTH-01~05, D-39).
 *
 * <p>{@code /signup,/login,/refresh}는 permitAll(인증 전 진입), {@code /logout}은 인증된 요청만
 * 허용한다(I-1 — broad {@code /auth/**} permitAll에서 제외). 성공 응답은 {@link ApiResponse}로 감싼다.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.noContent();
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        // I-1: 인증 경로이므로 jwt는 정상적으로 non-null. 방어적 가드 — null이면 인가 실패로 처리해
        // 무음 블랙리스트 누락(토큰 잔존 보안 구멍)을 막는다.
        if (jwt == null) {
            throw new AccessDeniedException("인증된 토큰이 필요합니다");
        }
        authService.logout(jwt.getId(), jwt.getExpiresAt());
        return ResponseEntity.noContent().build();
    }
}
