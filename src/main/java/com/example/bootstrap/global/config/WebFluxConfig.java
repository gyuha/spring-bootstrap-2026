package com.example.bootstrap.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * Spring WebFlux 전역 설정.
 *
 * <p>CORS 정책을 프로파일별로 구성합니다.
 * local: localhost:3000, localhost:5173 허용
 * prod: CORS_ALLOWED_ORIGINS 환경변수 기반
 */
@Configuration
public class WebFluxConfig implements WebFluxConfigurer {

    /** 허용된 CORS 오리진 목록. */
    @Value("${cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;

    /**
     * CORS 매핑을 구성합니다.
     *
     * @param registry {@link CorsRegistry} 인스턴스
     */
    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("*")
            .exposedHeaders("Authorization")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
