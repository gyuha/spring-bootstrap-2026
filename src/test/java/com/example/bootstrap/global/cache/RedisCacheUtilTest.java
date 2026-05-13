package com.example.bootstrap.global.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisCacheUtil}.
 *
 * <p>{@link ReactiveRedisTemplate} is fully mocked via Mockito so no
 * Redis server or Testcontainers instance is required.
 * All reactive assertions use Project Reactor's {@link StepVerifier}.</p>
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheUtilTest {

    private static final String CACHE_KEY = "account:profile:1";
    private static final String CACHE_VALUE = "{\"id\":1,\"email\":\"user@example.com\"}";
    private static final Duration TTL_5_MIN = Duration.ofMinutes(5);

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RedisCacheUtil redisCacheUtil;

    @BeforeEach
    void setUp() {
        redisCacheUtil = new RedisCacheUtil(redisTemplate);
    }

    // ── put ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("put: Redis set 성공 시 true를 반환한다")
    void put_whenRedisSetSucceeds_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(CACHE_KEY, CACHE_VALUE, TTL_5_MIN))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(redisCacheUtil.put(CACHE_KEY, CACHE_VALUE, TTL_5_MIN))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
    }

    @Test
    @DisplayName("put: Redis가 빈 Mono를 반환하면 false를 반환한다")
    void put_whenRedisReturnsEmptyMono_returnsFalse() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(redisCacheUtil.put(CACHE_KEY, CACHE_VALUE, TTL_5_MIN))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("put: 정확한 key, value, TTL로 Redis set을 호출한다")
    void put_callsRedisSetWithExactKeyValueAndTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(redisCacheUtil.put(CACHE_KEY, CACHE_VALUE, TTL_5_MIN))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(keyCaptor.capture(), valueCaptor.capture(), ttlCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo(CACHE_KEY);
        assertThat(valueCaptor.getValue()).isEqualTo(CACHE_VALUE);
        assertThat(ttlCaptor.getValue()).isEqualTo(TTL_5_MIN);
    }

    @Test
    @DisplayName("put: opsForValue().set()을 정확히 한 번 호출한다")
    void put_callsRedisSetExactlyOnce() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(redisCacheUtil.put(CACHE_KEY, CACHE_VALUE, TTL_5_MIN))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        verify(valueOperations, times(1)).set(anyString(), anyString(), any(Duration.class));
        verifyNoMoreInteractions(valueOperations);
    }

    @Test
    @DisplayName("put: 다양한 TTL 값으로 정상 동작한다 (1초 ~ 14일)")
    void put_withVariousTtlValues_succeedsForAllDurations() {
        Duration[] durations = {
            Duration.ofSeconds(1),
            Duration.ofMinutes(30),
            Duration.ofHours(1),
            Duration.ofDays(14)
        };

        for (Duration ttl : durations) {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.set(anyString(), anyString(), eq(ttl)))
                    .thenReturn(Mono.just(Boolean.TRUE));

            StepVerifier.create(redisCacheUtil.put(CACHE_KEY, CACHE_VALUE, ttl))
                    .expectNext(Boolean.TRUE)
                    .verifyComplete();
        }
    }

    // ── get ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("get: 키가 존재하면 저장된 값을 반환한다")
    void get_whenKeyExists_returnsStoredValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(Mono.just(CACHE_VALUE));

        StepVerifier.create(redisCacheUtil.get(CACHE_KEY))
                .expectNext(CACHE_VALUE)
                .verifyComplete();
    }

    @Test
    @DisplayName("get: 키가 존재하지 않으면 빈 Mono를 반환한다")
    void get_whenKeyAbsent_returnsEmptyMono() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(Mono.empty());

        StepVerifier.create(redisCacheUtil.get(CACHE_KEY))
                .verifyComplete();
    }

    @Test
    @DisplayName("get: 키가 만료(TTL 경과)된 경우 빈 Mono를 반환한다")
    void get_whenKeyExpired_returnsEmptyMono() {
        // Redis TTL 만료는 키 부재와 동일하게 빈 Mono로 표현된다
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(CACHE_KEY)).thenReturn(Mono.empty());

        StepVerifier.create(redisCacheUtil.get(CACHE_KEY))
                .expectNextCount(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("get: 올바른 키로 Redis get을 호출한다")
    void get_callsRedisGetWithCorrectKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.just(CACHE_VALUE));

        StepVerifier.create(redisCacheUtil.get(CACHE_KEY))
                .expectNext(CACHE_VALUE)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(CACHE_KEY);
    }

    @Test
    @DisplayName("get: opsForValue().get()을 정확히 한 번 호출한다")
    void get_callsRedisGetExactlyOnce() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(Mono.just(CACHE_VALUE));

        StepVerifier.create(redisCacheUtil.get(CACHE_KEY))
                .expectNext(CACHE_VALUE)
                .verifyComplete();

        verify(valueOperations, times(1)).get(anyString());
        verifyNoMoreInteractions(valueOperations);
    }

    // ── evict ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("evict: 키가 존재하면 1을 반환한다")
    void evict_whenKeyExists_returnsOne() {
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(Mono.just(1L));

        StepVerifier.create(redisCacheUtil.evict(CACHE_KEY))
                .expectNext(1L)
                .verifyComplete();
    }

    @Test
    @DisplayName("evict: 키가 존재하지 않으면 0을 반환한다")
    void evict_whenKeyAbsent_returnsZero() {
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(Mono.just(0L));

        StepVerifier.create(redisCacheUtil.evict(CACHE_KEY))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("evict: 만료된 키(TTL 경과 후)를 삭제하면 0을 반환한다")
    void evict_whenKeyAlreadyExpired_returnsZero() {
        // TTL이 만료되어 키가 없으면 삭제 개수 0
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(Mono.just(0L));

        StepVerifier.create(redisCacheUtil.evict(CACHE_KEY))
                .expectNext(0L)
                .verifyComplete();
    }

    @Test
    @DisplayName("evict: 올바른 키로 Redis delete를 호출한다")
    void evict_callsRedisDeleteWithCorrectKey() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(redisCacheUtil.evict(CACHE_KEY))
                .expectNext(1L)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).delete(keyCaptor.capture());
        assertThat(keyCaptor.getValue()).isEqualTo(CACHE_KEY);
    }

    @Test
    @DisplayName("evict: opsForValue()를 통하지 않고 직접 delete()를 호출한다")
    void evict_doesNotUseOpsForValue() {
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));

        StepVerifier.create(redisCacheUtil.evict(CACHE_KEY))
                .expectNext(1L)
                .verifyComplete();

        verify(redisTemplate, never()).opsForValue();
    }

    // ── Round-trip (put → get → evict) ───────────────────────────────────────

    @Test
    @DisplayName("round-trip: put 후 get은 저장된 값을 반환한다")
    void roundTrip_putThenGet_returnsStoredValue() {
        // put
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(CACHE_KEY, CACHE_VALUE, TTL_5_MIN))
                .thenReturn(Mono.just(Boolean.TRUE));
        // get — 같은 키에 저장된 값 반환
        when(valueOperations.get(CACHE_KEY)).thenReturn(Mono.just(CACHE_VALUE));

        Mono<String> roundTrip = redisCacheUtil
                .put(CACHE_KEY, CACHE_VALUE, TTL_5_MIN)
                .then(redisCacheUtil.get(CACHE_KEY));

        StepVerifier.create(roundTrip)
                .expectNext(CACHE_VALUE)
                .verifyComplete();
    }

    @Test
    @DisplayName("round-trip: put 후 evict 후 get은 빈 Mono를 반환한다")
    void roundTrip_putThenEvictThenGet_returnsEmpty() {
        // put
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(CACHE_KEY, CACHE_VALUE, TTL_5_MIN))
                .thenReturn(Mono.just(Boolean.TRUE));
        // evict
        when(redisTemplate.delete(CACHE_KEY)).thenReturn(Mono.just(1L));
        // get after evict — key is gone
        when(valueOperations.get(CACHE_KEY)).thenReturn(Mono.empty());

        Mono<String> roundTrip = redisCacheUtil
                .put(CACHE_KEY, CACHE_VALUE, TTL_5_MIN)
                .then(redisCacheUtil.evict(CACHE_KEY))
                .then(redisCacheUtil.get(CACHE_KEY));

        StepVerifier.create(roundTrip)
                .verifyComplete();
    }

    @Test
    @DisplayName("round-trip: 다른 키의 put은 기존 키의 get에 영향을 주지 않는다")
    void roundTrip_putDifferentKey_doesNotAffectExistingKey() {
        String otherKey = "account:profile:999";
        String otherValue = "{\"id\":999}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(otherKey), eq(otherValue), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));
        when(valueOperations.get(CACHE_KEY)).thenReturn(Mono.just(CACHE_VALUE));

        Mono<String> sequence = redisCacheUtil
                .put(otherKey, otherValue, TTL_5_MIN)
                .then(redisCacheUtil.get(CACHE_KEY));

        StepVerifier.create(sequence)
                .expectNext(CACHE_VALUE)
                .verifyComplete();
    }

    // ── JWT 블랙리스트 시나리오 (캐시 유틸 관점) ─────────────────────────────

    @Test
    @DisplayName("JWT 블랙리스트: jwt:blacklist: 접두사 키로 put하면 동일 키로 get 가능하다")
    void jwtBlacklist_putWithBlacklistPrefix_getReturnsSameValue() {
        String jwtKey = "jwt:blacklist:eyJhbGciOiJIUzI1NiJ9.some.token";
        String flagValue = "1";
        Duration accessTokenTtl = Duration.ofMinutes(30);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(jwtKey, flagValue, accessTokenTtl))
                .thenReturn(Mono.just(Boolean.TRUE));
        when(valueOperations.get(jwtKey)).thenReturn(Mono.just(flagValue));

        Mono<String> sequence = redisCacheUtil
                .put(jwtKey, flagValue, accessTokenTtl)
                .then(redisCacheUtil.get(jwtKey));

        StepVerifier.create(sequence)
                .expectNext(flagValue)
                .verifyComplete();
    }

    @Test
    @DisplayName("JWT 블랙리스트: 블랙리스트 키 TTL 만료 후 get은 빈 Mono를 반환한다")
    void jwtBlacklist_afterTtlExpiry_getReturnsEmpty() {
        String jwtKey = "jwt:blacklist:eyJhbGciOiJIUzI1NiJ9.some.token";

        // TTL 만료 시뮬레이션: Redis가 더 이상 해당 키를 반환하지 않음
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(jwtKey)).thenReturn(Mono.empty());

        StepVerifier.create(redisCacheUtil.get(jwtKey))
                .verifyComplete();
    }

    @Test
    @DisplayName("JWT 블랙리스트: evict는 블랙리스트 키를 제거하고 1을 반환한다")
    void jwtBlacklist_evict_removesBlacklistKey() {
        String jwtKey = "jwt:blacklist:eyJhbGciOiJIUzI1NiJ9.some.token";

        when(redisTemplate.delete(jwtKey)).thenReturn(Mono.just(1L));

        StepVerifier.create(redisCacheUtil.evict(jwtKey))
                .expectNext(1L)
                .verifyComplete();

        verify(redisTemplate).delete(jwtKey);
    }
}
