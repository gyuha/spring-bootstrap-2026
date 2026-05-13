package com.example.bootstrap.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * OpenAPI (Swagger) 설정 클래스.
 *
 * <p>local 프로파일에서만 활성화되며, API 메타정보와 JWT Bearer 인증 스킴을 정의합니다.
 * prod 프로파일에서는 {@code springdoc.api-docs.enabled=false} 설정으로 비활성화됩니다.
 */
@Configuration
@Profile("local")
public class OpenApiConfig {

    /** Swagger SecurityScheme 식별자. */
    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    /**
     * OpenAPI Bean을 생성합니다.
     *
     * <p>전역 보안 요구사항(JWT Bearer)과 API 기본 정보를 포함합니다.
     *
     * @return 설정된 {@link OpenAPI} 인스턴스
     */
    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, bearerSecurityScheme()));
    }

    /**
     * API 메타정보(제목, 설명, 버전, 연락처, 라이선스)를 반환합니다.
     *
     * @return {@link Info} 인스턴스
     */
    private Info apiInfo() {
        return new Info()
                .title("Spring Bootstrap API")
                .description("Java 21 + Spring Boot 3.4.x 기반 WebFlux/R2DBC 리액티브 모놀리식 스타터 템플릿 API")
                .version("v1.0.0")
                .contact(new Contact()
                        .name("Bootstrap Team")
                        .email("contact@example.com")
                        .url("https://github.com/example/spring-bootstrap"))
                .license(new License()
                        .name("MIT License")
                        .url("https://opensource.org/licenses/MIT"));
    }

    /**
     * JWT Bearer 인증 스킴을 정의합니다.
     *
     * <p>HTTP Bearer 방식의 HS256 JWT 토큰을 사용합니다.
     * Swagger UI에서 {@code Authorization: Bearer {token}} 헤더로 요청에 포함됩니다.
     *
     * @return {@link SecurityScheme} 인스턴스
     */
    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Access Token. 'Authorize' 버튼 클릭 후 발급받은 토큰을 입력하세요.");
    }
}
