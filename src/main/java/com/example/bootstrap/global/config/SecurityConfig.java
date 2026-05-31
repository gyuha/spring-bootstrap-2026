package com.example.bootstrap.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityFilterChain 명시 선언 (D-19).
 *
 * <p>Spring Boot 4는 secure-by-default이므로 체인을 명시하지 않으면 모든 엔드포인트가
 * 인증을 요구해 의도치 않게 잠긴다. Phase 1은 배선/스캐폴드 수준만 — 모든 경로 permitAll.
 *
 * <p>Phase 2에서 JWT 인증 필터(Nimbus oauth2-resource-server)와 USER/ADMIN 인가 규칙을 추가한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
