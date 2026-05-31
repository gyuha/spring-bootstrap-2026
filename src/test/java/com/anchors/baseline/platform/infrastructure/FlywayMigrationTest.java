package com.anchors.baseline.platform.infrastructure;

import com.anchors.baseline.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLAT-03: Flyway 마이그레이션이 실행되어 {@code flyway_schema_history} 에
 * 성공 레코드가 기록되는지 검증한다.
 */
class FlywayMigrationTest extends AbstractIntegrationTest {

    @Autowired
    DataSource dataSource;

    @Test
    void flywaySchemaHistoryExists() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT count(*) FROM flyway_schema_history WHERE success = true")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void flywayPlatformSampleTableExists() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT count(*) FROM information_schema.tables WHERE table_name = 'platform_sample'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }
}
