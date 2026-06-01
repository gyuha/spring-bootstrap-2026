package com.example.bootstrap.global.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 인증 설정 바인딩 (D-22/D-23).
 *
 * <p>{@code auth.jwt.*} 프로퍼티를 바인딩한다. {@code access-ttl}/{@code refresh-ttl}은
 * {@code application.yml}에서, {@code secret}(HS256 대칭키, >=32byte)은 환경별로 외부화한다
 * (로컬은 {@code application-local.yml}, 운영은 환경변수/시크릿 매니저 주입).
 *
 * <p>등록: {@code BootstrapApplication}의 {@code @EnableConfigurationProperties}.
 *
 * @param accessTtl  Access 토큰 수명 (기본 30분, D-23)
 * @param refreshTtl Refresh 토큰 수명 (기본 14일, D-23)
 * @param secret     HS256 대칭키 시크릿 (>=32byte, D-22)
 */
@ConfigurationProperties("auth.jwt")
public record AuthProperties(Duration accessTtl, Duration refreshTtl, String secret) {
}
