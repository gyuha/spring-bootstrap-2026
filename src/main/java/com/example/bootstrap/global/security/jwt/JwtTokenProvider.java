package com.example.bootstrap.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * Generates and validates HS256 JWT tokens.
 *
 * <p>Produces access tokens (30 min) and refresh tokens (14 days).
 * All token operations are pure in-memory and safe to call from any thread.</p>
 */
@Component
public class JwtTokenProvider {

    /** Claim key for the account e-mail address. */
    static final String CLAIM_EMAIL = "email";

    /** Claim key for the RBAC role (USER / ADMIN). */
    static final String CLAIM_ROLE = "role";

    /** Claim key distinguishing access vs. refresh tokens. */
    static final String CLAIM_TYPE = "type";

    /** Type value embedded in access tokens. */
    static final String TYPE_ACCESS = "access";

    /** Type value embedded in refresh tokens. */
    static final String TYPE_REFRESH = "refresh";

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;
    private final long refreshTokenExpiryMs;

    /**
     * Constructs the provider using the supplied JWT properties.
     *
     * @param jwtProperties application JWT configuration
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = jwtProperties.accessTokenExpiry() * 1000L;
        this.refreshTokenExpiryMs = jwtProperties.refreshTokenExpiry() * 1000L;
    }

    /**
     * Generates a signed JWT access token.
     *
     * @param userId account primary key
     * @param email  account e-mail address
     * @param role   RBAC role string (e.g. "USER")
     * @return compact signed JWT string
     */
    public String generateAccessToken(long userId, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generates a signed JWT refresh token.
     *
     * @param userId account primary key
     * @return compact signed JWT string
     */
    public String generateRefreshToken(long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiryMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses a compact JWT string and returns its payload claims.
     *
     * @param token compact JWT string
     * @return parsed {@link Claims}
     * @throws JwtException if the token signature is invalid or the token is expired
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates a token's signature and expiry without throwing.
     *
     * @param token compact JWT string
     * @return {@code true} if the token is well-formed, signed, and not expired
     */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extracts the account ID (JWT subject) from a token.
     *
     * @param token compact JWT string
     * @return account primary key
     */
    public long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * Returns the remaining validity duration of a token.
     *
     * <p>Returns {@link Duration#ZERO} if the computed remaining time is negative.</p>
     *
     * @param token compact JWT string
     * @return non-negative remaining duration
     */
    public Duration extractRemainingTtl(String token) {
        Date expiry = parseClaims(token).getExpiration();
        long remainingMs = expiry.getTime() - System.currentTimeMillis();
        return Duration.ofMillis(Math.max(remainingMs, 0L));
    }

    /**
     * Returns the absolute expiry timestamp of a token as a {@link LocalDateTime} in UTC.
     *
     * @param token compact JWT string
     * @return expiry instant expressed as a UTC {@link LocalDateTime}
     */
    public LocalDateTime extractExpiresAt(String token) {
        Date expiry = parseClaims(token).getExpiration();
        return LocalDateTime.ofInstant(expiry.toInstant(), ZoneOffset.UTC);
    }
}
