package com.example.bootstrap.global.security;

import com.example.bootstrap.domain.user.entity.Role;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * HS256 Access JWT 발급기 (AUTH-02, D-20/D-23/D-28).
 *
 * <p>subject=userId, role claim, jti, exp=now+access-ttl을 가진 HS256 JWT를 발급한다. 모든 시각은
 * {@code Instant}(UTC, D-13). 반환값에 jti/exp를 노출해 로그아웃 블랙리스트 TTL 계산(D-28)에 쓴다.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final NimbusJwtEncoder encoder;
    private final AuthProperties props;

    /**
     * Access JWT를 발급한다.
     *
     * @param userId subject로 들어갈 사용자 식별자
     * @param role   {@code role} claim (AUTH-06 인가용)
     * @return 토큰 값 + jti + exp
     */
    public IssuedAccess issueAccess(UUID userId, Role role) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.accessTtl());
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(exp)
                .id(jti)
                .claim("role", role.name())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedAccess(token, jti, exp);
    }

    /**
     * 발급된 Access의 토큰 값/jti/만료 시각.
     *
     * @param token 직렬화된 JWT
     * @param jti   토큰 식별자 (로그아웃 블랙리스트 키)
     * @param exp   만료 시각(UTC) — 블랙리스트 TTL 계산용
     */
    public record IssuedAccess(String token, String jti, Instant exp) {
    }
}
