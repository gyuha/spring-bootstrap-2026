package com.example.bootstrap.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 (FOUND-03).
 *
 * <p>{@code @CreatedDate}/{@code @LastModifiedDate}가 BaseEntity의 createdAt/updatedAt을
 * 자동 채우도록 {@link EnableJpaAuditing}을 켠다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
