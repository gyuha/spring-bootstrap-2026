package com.example.bootstrap.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Flyway V1 마이그레이션 적용 + ddl-auto=validate 부팅 검증 (FOUND-05).
 *
 * <p>V1__create_sample.sql이 SUCCESS 상태로 적용되고, samples 테이블이 실제로 조회 가능한지
 * 확인한다. Testcontainers는 fresh DB라 checksum mismatch를 가릴 수 있으므로(D-14), 영속 DB
 * 라이브 부팅 검증은 task 4(수동)에서 별도로 수행한다.
 */
class FlywayMigrationTest extends BaseIntegrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void flywayV1IsAppliedSuccessfully() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        MigrationInfo v1 = Arrays.stream(applied)
                .filter(m -> "1".equals(m.getVersion().getVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("V1 migration not found in applied migrations"));

        assertThat(v1.getState()).isEqualTo(MigrationState.SUCCESS);
    }

    @Test
    void samplesTableExistsAndIsQueryable() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM samples", Long.class);

        assertThat(count).isNotNull().isGreaterThanOrEqualTo(0L);
    }
}
