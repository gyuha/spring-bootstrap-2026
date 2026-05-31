package com.example.bootstrap.global;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway V1 마이그레이션 적용 + ddl-auto=validate 부팅 검증 (FOUND-05).
 *
 * <p>Wave 0 스캐폴드 — placeholder GREEN. 실제 assertion은 01-03에서 채운다.
 */
class FlywayMigrationTest extends BaseIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void placeholder_flywayV1Applied() {
        // TODO(01-03): flyway.info()로 V1 적용 + samples 테이블 존재 검증
        assertTrue(true);
    }
}
