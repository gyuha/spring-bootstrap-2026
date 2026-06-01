package com.example.bootstrap.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * decode 단계에서 Access jti 블랙리스트를 검사하는 Validator (AUTH-05, D-29).
 *
 * <p>{@link org.springframework.security.oauth2.jwt.NimbusJwtDecoder}에 주입되어 매 요청마다
 * {@code blacklist:{jti}} 존재 여부를 조회한다. 블랙리스트에 있으면 검증 실패(이후 401)로 처리한다.
 * {@code /auth/**}(permitAll, 토큰 없음) 경로는 decode가 일어나지 않아 불필요한 Redis 조회가 없다.
 */
@Component
@RequiredArgsConstructor
public class BlacklistTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redis;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String jti = token.getId();
        boolean blacklisted = jti != null
                && Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
        if (blacklisted) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("token_blacklisted", "토큰이 무효화되었습니다", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
