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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 인증 서비스.
 *
 * <p>로그인, 토큰 갱신(Rotation), 로그아웃을 처리합니다.
 */
@Service
public class AuthService {

    private final AccountRepository accountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklistService jwtBlacklistService;

    /**
     * @param accountRepository      계정 조회
     * @param refreshTokenRepository Refresh Token 저장/삭제
     * @param passwordEncoder        비밀번호 검증
     * @param jwtTokenProvider       JWT 생성/파싱
     * @param jwtBlacklistService    블랙리스트 관리
     */
    public AuthService(
            final AccountRepository accountRepository,
            final RefreshTokenRepository refreshTokenRepository,
            final PasswordEncoder passwordEncoder,
            final JwtTokenProvider jwtTokenProvider,
            final JwtBlacklistService jwtBlacklistService) {
        this.accountRepository = accountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    /**
     * 이메일/비밀번호로 로그인합니다.
     *
     * @param request 로그인 요청
     * @return 발급된 Access/Refresh 토큰
     */
    public Mono<TokenResponse> login(final LoginRequest request) {
        return accountRepository.findByEmail(request.email())
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.AUTH_004)))
                .flatMap(account -> {
                    if (account.getPassword() == null
                            || !passwordEncoder.matches(request.password(), account.getPassword())) {
                        return Mono.error(new BusinessException(ErrorCode.AUTH_004));
                    }
                    return issueTokens(account);
                });
    }

    /**
     * Refresh Token을 검증하고 새 토큰을 발급합니다 (Rotation).
     *
     * @param refreshToken 기존 Refresh Token
     * @return 새로 발급된 Access/Refresh 토큰
     */
    public Mono<TokenResponse> refresh(final String refreshToken) {
        if (!jwtTokenProvider.isValid(refreshToken)) {
            return Mono.error(new BusinessException(ErrorCode.AUTH_002));
        }
        return jwtBlacklistService.isBlacklisted(refreshToken)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        return Mono.<TokenResponse>error(new BusinessException(ErrorCode.AUTH_003));
                    }
                    return refreshTokenRepository.findByToken(refreshToken)
                            .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.AUTH_005)))
                            .flatMap(tokenEntity ->
                                    refreshTokenRepository.deleteByUserId(tokenEntity.getUserId())
                                            .then(accountRepository.findById(tokenEntity.getUserId()))
                                            .switchIfEmpty(Mono.error(
                                                    new BusinessException(ErrorCode.ACCOUNT_002)))
                                            .flatMap(this::issueTokens));
                });
    }

    /**
     * 로그아웃 — Access Token을 블랙리스트에 등록하고 Refresh Token을 삭제합니다.
     *
     * @param accessToken  현재 Access Token
     * @param refreshToken 현재 Refresh Token
     * @return 완료 시그널
     */
    public Mono<Void> logout(final String accessToken, final String refreshToken) {
        return jwtBlacklistService.addToBlacklist(accessToken)
                .then(refreshTokenRepository.findByToken(refreshToken)
                        .flatMap(refreshTokenRepository::delete));
    }

    private Mono<TokenResponse> issueTokens(final Account account) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                account.getId(), account.getEmail(), account.getRole());
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(account.getId());

        RefreshToken tokenEntity = new RefreshToken();
        tokenEntity.setUserId(account.getId());
        tokenEntity.setToken(newRefreshToken);
        tokenEntity.setExpiredAt(jwtTokenProvider.extractExpiresAt(newRefreshToken));

        return refreshTokenRepository.save(tokenEntity)
                .thenReturn(new TokenResponse(accessToken, newRefreshToken));
    }
}
