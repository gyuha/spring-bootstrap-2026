package com.example.bootstrap.global.cache;

import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Generic reactive Redis cache utility.
 *
 * <p>Provides thin put/get/evict operations on top of
 * {@link ReactiveRedisTemplate} without {@code @Cacheable} annotations,
 * keeping the reactive pipeline fully explicit.</p>
 *
 * <p>All keys are stored verbatim; callers are responsible for namespace
 * prefixing (e.g. {@code "account:profile:42"}).</p>
 */
@Component
public class RedisCacheUtil {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    /**
     * Constructs the utility with the reactive Redis template.
     *
     * @param redisTemplate Spring Data reactive Redis template
     */
    public RedisCacheUtil(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Stores a value under the given key with an explicit TTL.
     *
     * <p>Returns {@code true} when Redis confirms the write.
     * Returns {@code false} when Redis returns an empty signal.</p>
     *
     * @param key   Redis key
     * @param value string value to cache
     * @param ttl   positive time-to-live; must not be zero or negative
     * @return {@link Mono} emitting {@code true} on success
     */
    public Mono<Boolean> put(String key, String value, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(key, value, ttl)
                .defaultIfEmpty(Boolean.FALSE);
    }

    /**
     * Retrieves a cached value by key.
     *
     * <p>Returns an empty {@link Mono} when the key is absent or has expired.</p>
     *
     * @param key Redis key
     * @return {@link Mono} emitting the cached value, or empty if not found
     */
    public Mono<String> get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Deletes one or more keys from Redis.
     *
     * @param key Redis key to delete
     * @return {@link Mono} emitting the number of keys actually removed (0 or 1)
     */
    public Mono<Long> evict(String key) {
        return redisTemplate.delete(key);
    }
}
