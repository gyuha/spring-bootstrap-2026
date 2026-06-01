package com.example.bootstrap.domain.auth.service;

import com.example.bootstrap.domain.auth.dto.LoginRequest;
import com.example.bootstrap.domain.auth.dto.RefreshRequest;
import com.example.bootstrap.domain.auth.dto.SignupRequest;
import com.example.bootstrap.domain.auth.dto.TokenResponse;
import com.example.bootstrap.domain.user.entity.User;
import com.example.bootstrap.domain.user.repository.UserRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.security.AuthProperties;
import com.example.bootstrap.global.security.JwtTokenProvider;
import com.example.bootstrap.global.security.JwtTokenProvider.IssuedAccess;
import com.example.bootstrap.global.security.TokenBlacklist;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 흐름 오케스트레이션 — 가입/로그인/재발급/로그아웃 (AUTH-01~05).
 *
 * <p>"언제 발급/회전/무효화/차단하는가"를 결정한다. 토큰 발급/검증(02-02)·도메인 모델(02-01) 위에서
 * 동작한다. 단일 {@code JpaTransactionManager} 경계(D-08)를 따른다.
 *
 * <p>Refresh는 opaque {@code {userId}.{familyId}.{secret}} 형식으로 발급하고, secret의 SHA-256 해시만
 * Redis에 저장한다(평문 미저장). 사용자 열거 방지를 위해 로그인 실패는 email/password를 구분하지 않고
 * {@link ErrorCode#INVALID_CREDENTIALS} 단일 메시지로 응답한다(T-02-10).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBlacklist tokenBlacklist;
    private final AuthProperties authProperties;

    /**
     * 가입 — 중복 email이면 409, 아니면 BCrypt 해싱 후 저장. 토큰은 발급하지 않는다 (AUTH-01).
     */
    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        userRepository.save(User.create(request.email(), passwordEncoder.encode(request.password())));
    }

    /**
     * 로그인 — 자격 검증 후 Access(JWT) + opaque Refresh(새 패밀리)를 발급한다 (AUTH-02).
     *
     * <p>email 부재/비밀번호 불일치 모두 {@link ErrorCode#INVALID_CREDENTIALS}(모호 메시지)로 처리한다.
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        UUID userId = user.getId();
        String familyId = UUID.randomUUID().toString();
        IssuedAccess access = jwtTokenProvider.issueAccess(userId, user.getRole());
        String secret = newSecret();
        refreshTokenStore.create(userId, familyId, RefreshTokenStore.sha256(secret),
                authProperties.refreshTtl());

        return new TokenResponse(access.token(), refreshToken(userId, familyId, secret));
    }

    /**
     * 재발급 — opaque Refresh를 파싱·회전한다. 회전 성공 시 새 Access + 새 Refresh(familyId 유지)를
     * 반환하고, 재사용/무효 토큰이면 {@link ErrorCode#REFRESH_REUSE_DETECTED}(401)로 거부한다
     * (AUTH-03/04). soft-delete 사용자는 {@code findById} @SQLRestriction으로 차단된다(D-35).
     */
    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String[] parts = request.refreshToken().split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        UUID userId;
        try {
            userId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        String familyId = parts[1];
        String submittedSecret = parts[2];

        // soft-delete된 사용자는 @SQLRestriction으로 조회되지 않음 → 재발급 차단 (D-35)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        String newSecret = newSecret();
        boolean rotated = refreshTokenStore.rotate(userId, familyId,
                RefreshTokenStore.sha256(submittedSecret), RefreshTokenStore.sha256(newSecret),
                authProperties.refreshTtl());
        if (!rotated) {
            throw new BusinessException(ErrorCode.REFRESH_REUSE_DETECTED);
        }

        IssuedAccess access = jwtTokenProvider.issueAccess(userId, user.getRole());
        return new TokenResponse(access.token(), refreshToken(userId, familyId, newSecret));
    }

    /**
     * 로그아웃 — 현재 Access의 jti를 블랙리스트에 등록한다(TTL=exp-now, D-28). Refresh 패밀리는
     * 건드리지 않는다(연구 Q2 확정 — D-28만, AUTH-05).
     *
     * @param jti 인증된 Access의 jti
     * @param exp 해당 Access의 만료 시각(UTC)
     */
    public void logout(String jti, Instant exp) {
        tokenBlacklist.add(jti, exp);
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static String refreshToken(UUID userId, String familyId, String secret) {
        return userId + "." + familyId + "." + secret;
    }
}
