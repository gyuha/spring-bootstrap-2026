package com.example.bootstrap.global.security.jwt;

import io.jsonwebtoken.JwtException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Manages a JWT token blacklist backed by Redis.
 *
 * <p>Tokens are stored under the key {@code jwt:blacklist:<token>} with a TTL equal
 * to their remaining validity.  Expired tokens are never written, and Redis
 * automatically evicts entries when their TTL elapses.</p>
 *
 * <p>Uses {@link ReactiveRedisTemplate} directly; no {@code @Cacheable} annotation
 * is used to keep the reactive pipeline explicit.</p>
 */
@Service
public class JwtBlacklistService {

    /** Redis key prefix for blacklisted tokens. */
    static final String BLACKLIST_PREFIX = "jwt:blacklist:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Creates the service with the required collaborators.
     *
     * @param redisTemplate    reactive Redis template (String to String)
     * @param jwtTokenProvider token utility for TTL extraction
     */
    public JwtBlacklistService(ReactiveRedisTemplate<String, String> redisTemplate,
            JwtTokenProvider jwtTokenProvider) {
        this.redisTemplate = redisTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Adds a JWT token to the blacklist with a TTL derived from its remaining validity.
     *
     * <p>If the token is already expired (remaining TTL le 0) the operation is a no-op
     * and returns {@code true} immediately without writing to Redis.</p>
     *
     * @param token compact JWT string to blacklist
     * @return {@link Mono} emitting {@code true} on success
     */
    public Mono<Boolean> addToBlacklist(String token) {
        Duration ttl;
        try {
            ttl = jwtTokenProvider.extractRemainingTtl(token);
        } catch (JwtException e) {
            // Token is already expired — it will be rejected on its own; no Redis write needed.
            return Mono.just(Boolean.TRUE);
        }
        if (ttl.isZero() || ttl.isNegative()) {
            return Mono.just(Boolean.TRUE);
        }
        String key = BLACKLIST_PREFIX + token;
        return redisTemplate.opsForValue()
                .set(key, "1", ttl)
                .defaultIfEmpty(Boolean.FALSE);
    }

    /**
     * Checks whether a token is present in the blacklist.
     *
     * @param token compact JWT string to check
     * @return {@link Mono} emitting {@code true} if the token is blacklisted
     */
    public Mono<Boolean> isBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + token)
                .defaultIfEmpty(Boolean.FALSE);
    }
}
