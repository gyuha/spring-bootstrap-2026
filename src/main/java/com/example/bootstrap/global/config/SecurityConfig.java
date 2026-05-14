package com.example.bootstrap.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security WebFlux 보안 설정.
 *
 * <p>JWT Stateless 인증 기반의 보안 정책을 정의합니다.
 * Actuator, Swagger, 인증 관련 엔드포인트는 인증 없이 접근 가능합니다.
 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    /**
     * 보안 필터 체인을 구성합니다.
     *
     * <p>CSRF 비활성화, JWT 기반 상태 없는(Stateless) 인증,
     * 역할(Role) 기반 접근 제어를 설정합니다.
     *
     * @param http {@link ServerHttpSecurity} 인스턴스
     * @return 구성된 {@link SecurityWebFilterChain}
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(final ServerHttpSecurity http) {
        return http
            .csrf(csrf -> csrf.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(Customizer.withDefaults())
            .authorizeExchange(exchanges -> exchanges
                // Actuator 엔드포인트
                .pathMatchers(
                    "/actuator/health",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/actuator/prometheus"
                ).permitAll()
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                // Swagger/OpenAPI (local 프로파일)
                .pathMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/webjars/**"
                ).permitAll()
                // 인증 API
                .pathMatchers(HttpMethod.POST, "/api/v1/auth/**").permitAll()
                // 어드민 API
                .pathMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // 그 외 모든 요청은 인증 필요
                .anyExchange().authenticated()
            )
            .build();
    }

    /**
     * BCrypt 비밀번호 인코더 Bean을 등록합니다.
     *
     * @return {@link BCryptPasswordEncoder} 인스턴스
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Profile("local")
    public MapReactiveUserDetailsService localUserDetailsService(final PasswordEncoder encoder) {
        return new MapReactiveUserDetailsService(
            User.withUsername("user").password(encoder.encode("user")).roles("USER").build()
        );
    }
}
