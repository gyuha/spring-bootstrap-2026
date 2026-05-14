package com.example.bootstrap.global.security.jwt;

import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * JWT 인증 필터.
 *
 * <p>모든 HTTP 요청에서 {@code Authorization: Bearer <token>} 헤더를 검사합니다.
 * 유효한 토큰이면 {@link ReactiveSecurityContextHolder}에 Authentication을 설정하고,
 * 없거나 유효하지 않으면 설정 없이 다음 필터로 통과시킵니다.
 *
 * <p>인증 여부 최종 판단은 {@code authorizeExchange} 규칙이 담당합니다.
 */
@Component
public class JwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtBlacklistService jwtBlacklistService;

    /**
     * @param jwtTokenProvider   토큰 검증/파싱
     * @param jwtBlacklistService 블랙리스트 확인
     */
    public JwtAuthenticationFilter(
            final JwtTokenProvider jwtTokenProvider,
            final JwtBlacklistService jwtBlacklistService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.jwtBlacklistService = jwtBlacklistService;
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, final WebFilterChain chain) {
        String token = resolveToken(exchange.getRequest());
        if (token == null) {
            return chain.filter(exchange);
        }
        return Mono.defer(() -> {
            Claims claims = tryParseClaims(token);
            if (claims == null) {
                return chain.filter(exchange);
            }
            return jwtBlacklistService.isBlacklisted(token)
                .flatMap(blacklisted -> {
                    if (blacklisted) {
                        return chain.filter(exchange);
                    }
                    try {
                        Long userId = Long.parseLong(claims.getSubject());
                        String role = claims.get(JwtTokenProvider.CLAIM_ROLE, String.class);
                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                userId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                    } catch (NumberFormatException e) {
                        return chain.filter(exchange);
                    }
                });
        });
    }

    private Claims tryParseClaims(final String token) {
        try {
            return jwtTokenProvider.parseClaims(token);
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveToken(final ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
