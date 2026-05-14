package com.example.bootstrap.global.security.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "valid.jwt.token";
    private static final String BEARER_TOKEN = "Bearer " + TOKEN;

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtBlacklistService jwtBlacklistService;
    @Mock private WebFilterChain chain;

    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider, jwtBlacklistService);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 필터를 통과시키고 SecurityContext를 설정하지 않는다")
    void filter_withNoAuthorizationHeader_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test").build());

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 필터를 통과시킨다")
    void filter_withInvalidToken_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN).build());

        when(jwtTokenProvider.parseClaims(TOKEN)).thenThrow(new io.jsonwebtoken.JwtException("invalid"));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(jwtBlacklistService, never()).isBlacklisted(TOKEN);
    }

    @Test
    @DisplayName("블랙리스트 토큰이면 필터를 통과시킨다")
    void filter_withBlacklistedToken_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN).build());

        Claims claims = mock(Claims.class);
        when(jwtTokenProvider.parseClaims(TOKEN)).thenReturn(claims);
        when(jwtBlacklistService.isBlacklisted(TOKEN)).thenReturn(Mono.just(true));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(jwtTokenProvider, never()).isValid(TOKEN);
    }

    @Test
    @DisplayName("유효한 토큰이면 SecurityContext에 userId(Long)와 ROLE을 가진 Authentication을 설정한다")
    void filter_withValidToken_setsAuthenticationInSecurityContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(HttpHeaders.AUTHORIZATION, BEARER_TOKEN).build());

        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("42");
        when(claims.get(JwtTokenProvider.CLAIM_ROLE, String.class)).thenReturn("USER");

        when(jwtTokenProvider.parseClaims(TOKEN)).thenReturn(claims);
        when(jwtBlacklistService.isBlacklisted(TOKEN)).thenReturn(Mono.just(false));

        AtomicReference<Authentication> captured = new AtomicReference<>();
        when(chain.filter(exchange)).thenAnswer(inv ->
                ReactiveSecurityContextHolder.getContext()
                        .doOnNext(ctx -> captured.set(ctx.getAuthentication()))
                        .then());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().getPrincipal()).isEqualTo(42L);
        assertThat(captured.get().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("Bearer 접두사가 없는 Authorization 헤더는 토큰으로 처리하지 않는다")
    void filter_withNonBearerAuthorizationHeader_passesThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz").build());

        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verifyNoInteractions(jwtTokenProvider);
    }
}
