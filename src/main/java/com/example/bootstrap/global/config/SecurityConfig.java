package com.example.bootstrap.global.config;

import com.example.bootstrap.global.security.ProblemDetailAccessDeniedHandler;
import com.example.bootstrap.global.security.ProblemDetailAuthEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * JWT 인증 + 경로/메서드 인가 (D-36/37/38, AUTH-06).
 *
 * <p>Phase 1의 {@code anyRequest().permitAll()} 스캐폴드를 JWT 인증·인가로 전환한다. STATELESS
 * 세션·CSRF disable은 D-19 계승. 검증은 {@code oauth2ResourceServer().jwt()}가 {@code NimbusJwtDecoder}
 * 빈(서명·exp·블랙리스트 Validator 합성)을 자동 사용하고, {@code role} claim을 {@code ROLE_} authority로
 * 매핑한다.
 *
 * <p>매처 순서(I-2, Pitfall 5): permitAll 매처를 먼저, {@code anyRequest().authenticated()}를 마지막에 둔다.
 * 인증 진입 실패는 401({@link ProblemDetailAuthEntryPoint}), 권한 부족은 403
 * ({@link ProblemDetailAccessDeniedHandler})로 ProblemDetail 응답한다.
 *
 * <p>I-1: {@code /auth/logout}은 broad {@code /auth/**} permitAll에서 제외한다. 회원 진입 전 경로
 * ({@code /auth/signup,/login,/refresh})만 permitAll로 좁히고, {@code /auth/logout}은 명시하지 않아
 * {@code anyRequest().authenticated()}에 걸려 인증을 요구한다(AUTH-05는 logout이 인증 경로일 것을 요구).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            ProblemDetailAuthEntryPoint entryPoint,
            ProblemDetailAccessDeniedHandler deniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // I-1: 진입 전 경로만 permitAll — /auth/logout은 제외(인증 필요)
                        .requestMatchers(HttpMethod.POST,
                                "/auth/signup", "/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**").permitAll()
                        // I-2: 그 외 전부 인증 — 미인증 GET /samples → 401
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler));
        return http.build();
    }

    /** claim {@code role}=USER/ADMIN → {@code ROLE_USER}/{@code ROLE_ADMIN} authority (AUTH-06). */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("ROLE_");
        authorities.setAuthoritiesClaimName("role");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
