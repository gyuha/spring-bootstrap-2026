package com.example.bootstrap.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

/**
 * R2DBC 설정 클래스.
 *
 * <p>R2DBC 리포지토리를 활성화하고 감사(Auditing)를 구성합니다.
 * 런타임 데이터 접근은 R2DBC를 사용하고,
 * Flyway 마이그레이션과 Spring Batch JobRepository는 JDBC를 공유합니다.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.example.bootstrap")
@EnableR2dbcAuditing
public class R2dbcConfig {
}
