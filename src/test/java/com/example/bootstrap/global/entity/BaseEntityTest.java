package com.example.bootstrap.global.entity;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bootstrap.global.config.JpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BaseEntity audit/soft-delete/낙관적 락 검증 (FOUND-03).
 *
 * <p>Wave 0 스캐폴드 — placeholder GREEN. 실제 assertion은 01-03에서 채운다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@Testcontainers
class BaseEntityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Test
    void placeholder_baseEntityAudit() {
        // TODO(01-03): createdAt/updatedAt이 UTC Instant로 자동 채워지는지 검증
        assertTrue(true);
    }

    @Test
    void placeholder_softDeleteFilter() {
        // TODO(01-03): softDelete() 후 @SQLRestriction이 조회에서 제외하는지 검증
        assertTrue(true);
    }
}
