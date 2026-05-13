package com.example.bootstrap.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtTokenProvider}.
 *
 * <p>No Spring context is loaded — the provider is constructed directly with test properties.</p>
 */
class JwtTokenProviderTest {

    /** HS256 requires at least 256-bit (32-byte) secret. */
    private static final String TEST_SECRET = "test-secret-key-must-be-32-chars-long!!";

    private static final long ACCESS_EXPIRY_SECONDS = 1800L;   // 30 minutes
    private static final long REFRESH_EXPIRY_SECONDS = 1209600L; // 14 days

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                TEST_SECRET, ACCESS_EXPIRY_SECONDS, REFRESH_EXPIRY_SECONDS);
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    // ── Access Token Generation ─────────────────────────────────────────────

    @Test
    @DisplayName("Access token contains correct subject, email, role, and type claims")
    void generateAccessToken_embedsCorrectClaims() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");

        Claims claims = jwtTokenProvider.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtTokenProvider.CLAIM_EMAIL, String.class))
                .isEqualTo("user@example.com");
        assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).isEqualTo("USER");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))
                .isEqualTo(JwtTokenProvider.TYPE_ACCESS);
    }

    @Test
    @DisplayName("Access token contains ADMIN role when role is ADMIN")
    void generateAccessToken_withAdminRole_embedsAdminRole() {
        String token = jwtTokenProvider.generateAccessToken(99L, "admin@example.com", "ADMIN");

        Claims claims = jwtTokenProvider.parseClaims(token);
        assertThat(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Access token issuedAt and expiration are set")
    void generateAccessToken_setsIssuedAtAndExpiration() {
        // JWT spec stores timestamps as epoch-seconds, so truncate beforeMs to seconds.
        long beforeSec = System.currentTimeMillis() / 1000L;
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        long afterSec = System.currentTimeMillis() / 1000L;

        Claims claims = jwtTokenProvider.parseClaims(token);
        long issuedAtSec = claims.getIssuedAt().getTime() / 1000L;
        long expirationSec = claims.getExpiration().getTime() / 1000L;

        assertThat(issuedAtSec).isBetween(beforeSec, afterSec);
        assertThat(expirationSec).isBetween(
                beforeSec + ACCESS_EXPIRY_SECONDS - 1L,
                afterSec + ACCESS_EXPIRY_SECONDS + 1L);
    }

    // ── Refresh Token Generation ────────────────────────────────────────────

    @Test
    @DisplayName("Refresh token contains correct subject and type=refresh")
    void generateRefreshToken_embedsSubjectAndRefreshType() {
        String token = jwtTokenProvider.generateRefreshToken(42L);

        Claims claims = jwtTokenProvider.parseClaims(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get(JwtTokenProvider.CLAIM_TYPE, String.class))
                .isEqualTo(JwtTokenProvider.TYPE_REFRESH);
    }

    @Test
    @DisplayName("Refresh token does not contain email claim")
    void generateRefreshToken_doesNotContainEmailClaim() {
        String token = jwtTokenProvider.generateRefreshToken(1L);

        Claims claims = jwtTokenProvider.parseClaims(token);
        assertThat(claims.get(JwtTokenProvider.CLAIM_EMAIL)).isNull();
    }

    // ── Token Validation ────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid freshly-issued access token passes isValid check")
    void isValid_withValidAccessToken_returnsTrue() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        assertThat(jwtTokenProvider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("Valid freshly-issued refresh token passes isValid check")
    void isValid_withValidRefreshToken_returnsTrue() {
        String token = jwtTokenProvider.generateRefreshToken(1L);
        assertThat(jwtTokenProvider.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("Appending arbitrary bytes to token fails isValid check")
    void isValid_withTamperedSignature_returnsFalse() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        assertThat(jwtTokenProvider.isValid(token + "x")).isFalse();
    }

    @Test
    @DisplayName("Null string fails isValid check")
    void isValid_withNullToken_returnsFalse() {
        assertThat(jwtTokenProvider.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("Empty string fails isValid check")
    void isValid_withEmptyToken_returnsFalse() {
        assertThat(jwtTokenProvider.isValid("")).isFalse();
    }

    @Test
    @DisplayName("Blank (whitespace-only) string fails isValid check")
    void isValid_withBlankToken_returnsFalse() {
        assertThat(jwtTokenProvider.isValid("   ")).isFalse();
    }

    @Test
    @DisplayName("Token signed with a different secret fails isValid check")
    void isValid_withForeignSecret_returnsFalse() {
        JwtProperties otherProps = new JwtProperties(
                "different-secret-key-must-be-32-chars!!", ACCESS_EXPIRY_SECONDS, REFRESH_EXPIRY_SECONDS);
        JwtTokenProvider other = new JwtTokenProvider(otherProps);
        String foreignToken = other.generateAccessToken(1L, "user@example.com", "USER");

        assertThat(jwtTokenProvider.isValid(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("Token with negative expiry (already expired) fails isValid check")
    void isValid_withExpiredToken_returnsFalse() {
        // Create a provider that generates tokens with negative TTL (already expired at issue)
        JwtProperties shortProps = new JwtProperties(TEST_SECRET, -1L, -1L);
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortProps);
        String expiredToken = shortProvider.generateAccessToken(1L, "user@example.com", "USER");

        assertThat(jwtTokenProvider.isValid(expiredToken)).isFalse();
    }

    // ── Exception Handling ──────────────────────────────────────────────────

    @Test
    @DisplayName("parseClaims throws JwtException for expired token")
    void parseClaims_withExpiredToken_throwsJwtException() {
        JwtProperties shortProps = new JwtProperties(TEST_SECRET, -1L, -1L);
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortProps);
        String expiredToken = shortProvider.generateAccessToken(1L, "user@example.com", "USER");

        assertThatThrownBy(() -> jwtTokenProvider.parseClaims(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("parseClaims throws JwtException for completely malformed token")
    void parseClaims_withGarbageString_throwsJwtException() {
        assertThatThrownBy(() -> jwtTokenProvider.parseClaims("not.a.jwt"))
                .isInstanceOf(JwtException.class);
    }

    // ── extractUserId ───────────────────────────────────────────────────────

    @Test
    @DisplayName("extractUserId returns the account ID embedded in the token")
    void extractUserId_returnsEmbeddedUserId() {
        String token = jwtTokenProvider.generateAccessToken(123L, "user@example.com", "USER");
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(123L);
    }

    @Test
    @DisplayName("extractUserId works for refresh tokens too")
    void extractUserId_fromRefreshToken_returnsEmbeddedUserId() {
        String token = jwtTokenProvider.generateRefreshToken(77L);
        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(77L);
    }

    // ── extractRemainingTtl ─────────────────────────────────────────────────

    @Test
    @DisplayName("Access token TTL is positive and at most 30 minutes")
    void extractRemainingTtl_freshAccessToken_isWithin30Minutes() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");

        Duration ttl = jwtTokenProvider.extractRemainingTtl(token);

        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofSeconds(ACCESS_EXPIRY_SECONDS));
    }

    @Test
    @DisplayName("Refresh token TTL is positive and at most 14 days")
    void extractRemainingTtl_freshRefreshToken_isWithin14Days() {
        String token = jwtTokenProvider.generateRefreshToken(1L);

        Duration ttl = jwtTokenProvider.extractRemainingTtl(token);

        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(Duration.ofSeconds(REFRESH_EXPIRY_SECONDS));
    }

    @Test
    @DisplayName("Access token TTL is approximately 30 minutes (within 5-second tolerance)")
    void extractRemainingTtl_accessToken_isApproximately30Minutes() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");

        Duration ttl = jwtTokenProvider.extractRemainingTtl(token);

        assertThat(ttl).isBetween(
                Duration.ofSeconds(ACCESS_EXPIRY_SECONDS - 5),
                Duration.ofSeconds(ACCESS_EXPIRY_SECONDS));
    }

    @Test
    @DisplayName("Refresh token TTL is approximately 14 days (within 5-second tolerance)")
    void extractRemainingTtl_refreshToken_isApproximately14Days() {
        String token = jwtTokenProvider.generateRefreshToken(1L);

        Duration ttl = jwtTokenProvider.extractRemainingTtl(token);

        assertThat(ttl).isBetween(
                Duration.ofSeconds(REFRESH_EXPIRY_SECONDS - 5),
                Duration.ofSeconds(REFRESH_EXPIRY_SECONDS));
    }

    // ── extractExpiresAt ────────────────────────────────────────────────────

    @Test
    @DisplayName("extractExpiresAt returns a UTC LocalDateTime approximately 30 min in the future")
    void extractExpiresAt_accessToken_returnsApproximately30MinutesFromNow() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");

        java.time.LocalDateTime expiresAt = jwtTokenProvider.extractExpiresAt(token);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

        assertThat(expiresAt).isAfter(now);
        assertThat(expiresAt).isBefore(now.plusSeconds(ACCESS_EXPIRY_SECONDS + 5));
    }

    // ── Token Uniqueness ────────────────────────────────────────────────────

    @Test
    @DisplayName("Two access tokens issued at least 1 second apart are different strings")
    void generateAccessToken_twoCallsOneSecondApart_produceDifferentTokens() throws InterruptedException {
        // JWT issuedAt is second-precision; sleep 1.1s to guarantee different timestamps.
        String token1 = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");
        Thread.sleep(1100); // advance past the 1-second boundary
        String token2 = jwtTokenProvider.generateAccessToken(1L, "user@example.com", "USER");

        assertThat(token1).isNotEqualTo(token2);
    }
}
