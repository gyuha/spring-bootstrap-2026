package com.example.bootstrap.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT configuration properties bound from application.yml (prefix: jwt).
 *
 * @param secret              HS256 symmetric signing secret (min 32 ASCII chars)
 * @param accessTokenExpiry   access-token lifetime in seconds (default 1800 = 30 min)
 * @param refreshTokenExpiry  refresh-token lifetime in seconds (default 1209600 = 14 days)
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenExpiry,
        long refreshTokenExpiry) {
}
