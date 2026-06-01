package com.example.bootstrap.global.security;

import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로그아웃된 Access의 jti를 Redis 블랙리스트에 등록한다 (AUTH-05, D-28).
 *
 * <p>키 패턴 {@code blacklist:{jti}}, 값 {@code "1"}, TTL = {@code exp - now}. 토큰 자연 만료와
 * 블랙리스트 만료를 일치시켜 메모리 누수를 방지한다. 이미 만료된 토큰(ttl 음수/0)은 등록하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String KEY_PREFIX = "blacklist:";

    private final StringRedisTemplate redis;

    /**
     * jti를 블랙리스트에 등록한다. TTL은 {@code exp - now}이며, 양수일 때만 등록한다 (D-28).
     *
     * @param jti 무효화할 Access 토큰의 jti
     * @param exp 해당 Access 토큰의 만료 시각(UTC)
     */
    public void add(String jti, Instant exp) {
        Duration ttl = Duration.between(Instant.now(), exp);
        if (!ttl.isNegative() && !ttl.isZero()) {
            redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        }
    }
}
