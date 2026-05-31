package com.anchors.baseline.auth.infrastructure.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * BFF 인증 필터 체인(D-01, Pattern 1) — oauth2Login·logout·CSRF·401 엔트리포인트를 선언한다.
 * 프레임워크 타입 결합 → infrastructure 배치. 토큰은 프레임워크 기본
 * {@code HttpSessionOAuth2AuthorizedClientRepository} 로 HttpSession→Redis 서버 측에만 저장된다
 * (커스텀 토큰 스토어 없음 — NFR-01/SC#1).
 *
 * <p>main 프로파일에는 OIDC registration 이 없어 {@link ClientRegistrationRepository} 빈이 없을 수 있다.
 * 이때 oauth2Login DSL 을 호출하면 {@code NoSuchBeanDefinitionException} 으로 컨텍스트 부팅이 깨지므로,
 * registration 존재 여부({@link ObjectProvider})로 oauth2Login 적용을 가드한다 — 빈 placeholder 를
 * main yml 에 두는 대신 부팅을 조건부로 유지한다(test/배포 프로파일에서만 registration 정의).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    OidcUserService baselineOidcUserService,
                                    ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository)
            throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/login/**", "/oauth2/**",
                                "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("SESSION"))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // SPA 는 쿠키의 raw 토큰을 X-XSRF-TOKEN 헤더로 그대로 회신한다 → plain handler 사용
                        // (기본 Xor handler 는 헤더에 XOR 인코딩 값을 요구해 raw 쿠키 값과 불일치).
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .exceptionHandling(ex -> ex
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                PathPatternRequestMatcher.withDefaults().matcher("/api/**")))
                // SPA/BFF CSRF: deferred 토큰은 application 이 토큰을 읽어야 XSRF-TOKEN 쿠키가 써진다.
                // SPA 는 컨트롤러에서 토큰을 읽지 않으므로, 매 요청마다 토큰을 강제로 materialize 해
                // 쿠키가 항상 내려가게 한다(프론트가 X-XSRF-TOKEN 헤더로 회신). D-06 표준 SPA 패턴.
                .addFilterAfter(new CsrfCookieFilter(), LogoutFilter.class);

        // oauth2Login 은 ClientRegistrationRepository 빈이 있을 때만 적용(main 프로파일 부팅 보전).
        if (clientRegistrationRepository.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(ui -> ui.oidcUserService(baselineOidcUserService)));
        } else {
            // 사일런트 미설정 방지(fail-loud): registration 부재 시 OIDC 로그인 경로가 비활성화되어
            // 모든 보호 요청이 401 로 잠긴다. 운영에서 OIDC 가 필요하면 시작 로그로 즉시 인지하도록 경고한다.
            log.warn("OIDC 로그인 비활성화 — ClientRegistrationRepository 빈이 없습니다. "
                    + "main 프로파일은 정상이나, 운영 배포 시 OIDC registration(예: spring.security.oauth2.client.*)이 "
                    + "누락되면 로그인 경로가 없어 모든 보호 엔드포인트가 401 로 잠깁니다.");
        }

        return http.build();
    }

    /**
     * 토큰(OAuth2AuthorizedClient)을 HttpSession attribute 로 보관해 Spring Session 을 타고 Redis 서버
     * 측에만 들어가게 한다(NFR-02/SC#1). 명시하지 않으면 Boot 기본은
     * {@code AuthenticatedPrincipalOAuth2AuthorizedClientRepository}(InMemory 서비스 위임)라 토큰이
     * Redis 세션이 아닌 애플리케이션 인메모리에 남아 수평 확장·세션 일관성을 깨고 SC#1 전제를 위반한다.
     * 프레임워크 기본 컴포넌트만 사용(커스텀 토큰 스토어 없음 — NFR-01).
     */
    @Bean
    OAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new HttpSessionOAuth2AuthorizedClientRepository();
    }

    /** 매 요청마다 CsrfToken 을 읽어 deferred 토큰을 materialize → XSRF-TOKEN 쿠키가 항상 응답에 실린다. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
