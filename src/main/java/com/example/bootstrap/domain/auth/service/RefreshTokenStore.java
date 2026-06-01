package com.example.bootstrap.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Refresh 패밀리 상태(회전·재사용 탐지·전량 무효화)를 Redis Lua로 원자 실행한다 (AUTH-03/04, D-24~27).
 *
 * <p>키 스키마: {@code refresh:{userId}:{familyId}} String=현재 유효 Refresh의 SHA-256 해시(TTL=Refresh TTL),
 * {@code refresh:idx:{userId}} Set=familyId 목록(전량 무효화용). 평문 Refresh는 저장하지 않고 해시만 둔다
 * (유출 시 즉시 악용 방지 — Information Disclosure 완화).
 *
 * <p>회전은 {@code rotate_refresh.lua}의 GET→비교→SET 단일 원자 실행으로 처리해, 가상 스레드 2개가 같은
 * Refresh로 동시 재발급을 시도해도 하나만 성공하게 한다(double-rotate race 차단, D-24/26). 전량 무효화는
 * {@code invalidate_family.lua}로 인덱스 Set을 순회하며 {@code KEYS *} 스캔을 쓰지 않는다(D-27).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "refresh:";
    private static final String IDX_PREFIX = "refresh:idx:";

    private static final RedisScript<Long> ROTATE =
            RedisScript.of(new ClassPathResource("redis/rotate_refresh.lua"), Long.class);
    private static final RedisScript<Long> INVALIDATE =
            RedisScript.of(new ClassPathResource("redis/invalidate_family.lua"), Long.class);

    private final StringRedisTemplate redis;

    /**
     * 로그인 시 새 패밀리의 현재 유효 Refresh 해시를 저장하고 인덱스에 familyId를 등록한다 (D-25).
     *
     * @param userId    사용자 식별자
     * @param familyId  새 패밀리 식별자
     * @param tokenHash Refresh secret의 SHA-256 해시
     * @param ttl       Refresh TTL
     */
    public void create(UUID userId, String familyId, String tokenHash, Duration ttl) {
        redis.opsForValue().set(key(userId, familyId), tokenHash, ttl);
        redis.opsForSet().add(idxKey(userId), familyId);
    }

    /**
     * 1회용 회전 — 제출 해시가 현재값과 일치하면 새 해시로 compare-and-set(true), 불일치/부재면 패밀리를
     * 무효화하고 false를 반환한다(재사용 탐지, D-24/26). 무효화는 Lua가 이미 수행한다.
     *
     * @return 회전 성공 {@code true} / 재사용 탐지 {@code false}
     */
    public boolean rotate(UUID userId, String familyId,
                          String submittedHash, String newHash, Duration ttl) {
        Long r = redis.execute(ROTATE,
                List.of(key(userId, familyId), idxKey(userId)),
                submittedHash, newHash, familyId, String.valueOf(ttl.toSeconds()));
        return r != null && r == 1L;
    }

    /**
     * userId의 모든 Refresh 패밀리를 무효화한다 (D-27, Phase 3 ADMIN-03 재사용).
     *
     * @return 무효화한 패밀리 수
     */
    public long invalidateAll(UUID userId) {
        Long n = redis.execute(INVALIDATE, List.of(idxKey(userId)), KEY_PREFIX + userId + ":");
        return n == null ? 0L : n;
    }

    /**
     * Refresh secret의 SHA-256 해시(hex). 평문 대신 이 값을 Redis에 저장/비교한다.
     */
    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String key(UUID userId, String familyId) {
        return KEY_PREFIX + userId + ":" + familyId;
    }

    private String idxKey(UUID userId) {
        return IDX_PREFIX + userId;
    }
}
