package com.example.bootstrap.global.security.jwt;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtBlacklistService}.
 *
 * <p>{@link ReactiveRedisTemplate} is mocked with Mockito so no Redis server is required.
 * All assertions are made via Project Reactor's {@link StepVerifier}.</p>
 */
@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-32-chars-long!!";
    private static final long ACCESS_EXPIRY_SECONDS = 1800L;
    private static final long REFRESH_EXPIRY_SECONDS = 1209600L;

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private JwtTokenProvider jwtTokenProvider;
    private JwtBlacklistService jwtBlacklistService;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                TEST_SECRET, ACCESS_EXPIRY_SECONDS, REFRESH_EXPIRY_SECONDS);
        jwtTokenProvider = new JwtTokenProvider(properties);
        jwtBlacklistService = new JwtBlacklistService(redisTemplate, jwtTokenProvider);
    }

    // ── addToBlacklist ──────────────────────────────────────────────────────

    @Test
    @DisplayName("addToBlacklist stores token under 'jwt:blacklist:<token>' key")
    void addToBlacklist_usesCorrectRedisKey() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(jwtBlacklistService.addToBlacklist(token))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), eq("1"), any(Duration.class));
        assertThat(keyCaptor.getValue())
                .startsWith(JwtBlacklistService.BLACKLIST_PREFIX)
                .endsWith(token);
    }

    @Test
    @DisplayName("addToBlacklist stores value '1' in Redis")
    void addToBlacklist_storesValueOne() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(jwtBlacklistService.addToBlacklist(token))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(anyString(), valueCaptor.capture(), any(Duration.class));
        assertThat(valueCaptor.getValue()).isEqualTo("1");
    }

    @Test
    @DisplayName("addToBlacklist sets TTL derived from access token expiry (~30 min)")
    void addToBlacklist_accessToken_setsTtlApproximately30Minutes() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(jwtBlacklistService.addToBlacklist(token))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(anyString(), anyString(), ttlCaptor.capture());
        Duration capturedTtl = ttlCaptor.getValue();

        assertThat(capturedTtl).isPositive();
        assertThat(capturedTtl).isBetween(
                Duration.ofSeconds(ACCESS_EXPIRY_SECONDS - 5),
                Duration.ofSeconds(ACCESS_EXPIRY_SECONDS));
    }

    @Test
    @DisplayName("addToBlacklist sets TTL derived from refresh token expiry (~14 days)")
    void addToBlacklist_refreshToken_setsTtlApproximately14Days() {
        String token = jwtTokenProvider.generateRefreshToken(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(jwtBlacklistService.addToBlacklist(token))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).set(anyString(), anyString(), ttlCaptor.capture());
        Duration capturedTtl = ttlCaptor.getValue();

        assertThat(capturedTtl).isGreaterThan(Duration.ofDays(13));
        assertThat(capturedTtl).isLessThanOrEqualTo(Duration.ofDays(14));
    }

    @Test
    @DisplayName("addToBlacklist emits true when Redis set succeeds")
    void addToBlacklist_whenRedisSucceeds_returnsTrue() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(jwtBlacklistService.addToBlacklist(token))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
    }

    @Test
    @DisplayName("addToBlacklist calls Redis opsForValue().set exactly once")
    void addToBlacklist_callsRedisSetExactlyOnce() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(jwtBlacklistService.addToBlacklist(token))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        verify(valueOperations, times(1)).set(anyString(), anyString(), any(Duration.class));
        verifyNoMoreInteractions(valueOperations);
    }

    @Test
    @DisplayName("addToBlacklist skips Redis write and returns true when token is already expired")
    void addToBlacklist_withExpiredToken_skipsRedisAndReturnsTrue() {
        // Token with negative TTL is already expired
        JwtProperties expiredProps = new JwtProperties(TEST_SECRET, -1L, -1L);
        JwtTokenProvider expiredProvider = new JwtTokenProvider(expiredProps);
        String expiredToken = expiredProvider.generateAccessToken(1L, "user@example.com", "USER");

        // No Redis interaction should occur — no mock setup needed
        StepVerifier.create(jwtBlacklistService.addToBlacklist(expiredToken))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
    }

    // ── isBlacklisted ───────────────────────────────────────────────────────

    @Test
    @DisplayName("isBlacklisted returns true when key exists in Redis")
    void isBlacklisted_whenKeyExists_returnsTrue() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        String expectedKey = JwtBlacklistService.BLACKLIST_PREFIX + token;
        when(redisTemplate.hasKey(expectedKey)).thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(jwtBlacklistService.isBlacklisted(token))
                .expectNext(Boolean.TRUE)
                .verifyComplete();

        verify(redisTemplate).hasKey(expectedKey);
    }

    @Test
    @DisplayName("isBlacklisted returns false when key does not exist in Redis")
    void isBlacklisted_whenKeyAbsent_returnsFalse() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        String expectedKey = JwtBlacklistService.BLACKLIST_PREFIX + token;
        when(redisTemplate.hasKey(expectedKey)).thenReturn(Mono.just(Boolean.FALSE));

        StepVerifier.create(jwtBlacklistService.isBlacklisted(token))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    @Test
    @DisplayName("isBlacklisted queries Redis with correct 'jwt:blacklist:<token>' key")
    void isBlacklisted_usesCorrectRedisKey() {
        String token = jwtTokenProvider.generateAccessToken(42L, "admin@example.com", "ADMIN");
        String expectedKey = JwtBlacklistService.BLACKLIST_PREFIX + token;
        when(redisTemplate.hasKey(expectedKey)).thenReturn(Mono.just(Boolean.FALSE));

        StepVerifier.create(jwtBlacklistService.isBlacklisted(token))
                .expectNext(Boolean.FALSE)
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).hasKey(keyCaptor.capture());
        assertThat(keyCaptor.getValue())
                .isEqualTo(JwtBlacklistService.BLACKLIST_PREFIX + token);
    }

    @Test
    @DisplayName("isBlacklisted returns false when Redis returns empty Mono")
    void isBlacklisted_whenRedisEmptyMono_returnsFalse() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(jwtBlacklistService.isBlacklisted(token))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }

    // ── Blacklist Round-trip ────────────────────────────────────────────────

    @Test
    @DisplayName("After addToBlacklist, isBlacklisted returns true for same token")
    void blacklistRoundTrip_addThenCheck_returnsTrue() {
        String token = jwtTokenProvider.generateAccessToken(5L, "user@example.com", "USER");
        String key = JwtBlacklistService.BLACKLIST_PREFIX + token;

        // Setup: add succeeds
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.set(eq(key), eq("1"), any(Duration.class)))
                .thenReturn(Mono.just(Boolean.TRUE));
        // Check: key now exists
        when(redisTemplate.hasKey(key)).thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(
                jwtBlacklistService.addToBlacklist(token)
                        .then(jwtBlacklistService.isBlacklisted(token)))
                .expectNext(Boolean.TRUE)
                .verifyComplete();
    }

    @Test
    @DisplayName("Token not added to blacklist results in isBlacklisted returning false")
    void isBlacklisted_tokenNotAdded_returnsFalse() {
        String token = jwtTokenProvider.generateAccessToken(5L, "user@example.com", "USER");
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.just(Boolean.FALSE));

        StepVerifier.create(jwtBlacklistService.isBlacklisted(token))
                .expectNext(Boolean.FALSE)
                .verifyComplete();
    }
}
