package com.example.bootstrap.global;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Flyway 마이그레이션 실행 및 스키마 검증 통합 테스트.
 *
 * <p>TestContainers PostgreSQL 을 사용하여 Flyway 마이그레이션이 성공적으로 실행되고,
 * flyway_schema_history 테이블 및 생성된 스키마가 올바른지 검증합니다.</p>
 *
 * <ul>
 *   <li>V1: Account 도메인 + AI 도메인 테이블 생성</li>
 *   <li>V2: Spring Batch 5.x 메타데이터 테이블 및 시퀀스 생성</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@Import(TestcontainersConfig.class)
@DisplayName("Flyway 마이그레이션 및 스키마 검증 통합 테스트")
class FlywayMigrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── Flyway 마이그레이션 이력 검증 ────────────────────────────────────────

    @Test
    @DisplayName("Flyway 마이그레이션 V1이 성공적으로 실행되어야 한다")
    void flyway_V1Migration_ShouldSucceed() {
        Map<String, Object> v1 = jdbcTemplate.queryForMap(
            "SELECT version, description, success "
                + "FROM flyway_schema_history WHERE version = '1'"
        );

        assertThat(v1.get("version")).isEqualTo("1");
        assertThat(v1.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Flyway 마이그레이션 V2가 성공적으로 실행되어야 한다")
    void flyway_V2Migration_ShouldSucceed() {
        Map<String, Object> v2 = jdbcTemplate.queryForMap(
            "SELECT version, description, success "
                + "FROM flyway_schema_history WHERE version = '2'"
        );

        assertThat(v2.get("version")).isEqualTo("2");
        assertThat(v2.get("success")).isEqualTo(true);
    }

    @Test
    @DisplayName("Flyway 마이그레이션이 총 2건 실행되어야 한다")
    void flyway_ShouldHaveExactlyTwoMigrations() {
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
            "SELECT version, success FROM flyway_schema_history "
                + "WHERE version IS NOT NULL ORDER BY installed_rank"
        );

        assertThat(history).hasSize(2);
        assertThat(history).allSatisfy(row ->
            assertThat(row.get("success")).isEqualTo(true)
        );
    }

    // ── Account 도메인 테이블 검증 ────────────────────────────────────────────

    @Test
    @DisplayName("users 테이블이 정상적으로 생성되어야 한다")
    void users_TableShouldExist() {
        assertTableExists("users");
    }

    @Test
    @DisplayName("users 테이블이 올바른 컬럼 구조를 가져야 한다")
    void users_TableShouldHaveCorrectColumns() {
        List<String> columns = queryColumnNames("users");

        assertThat(columns).containsExactlyInAnyOrder(
            "id", "email", "password", "nickname", "role",
            "email_verified", "profile_image_url", "created_at", "updated_at"
        );
    }

    @Test
    @DisplayName("refresh_tokens 테이블이 정상적으로 생성되어야 한다")
    void refreshTokens_TableShouldExist() {
        assertTableExists("refresh_tokens");
    }

    @Test
    @DisplayName("refresh_tokens 테이블이 올바른 컬럼 구조를 가져야 한다")
    void refreshTokens_TableShouldHaveCorrectColumns() {
        List<String> columns = queryColumnNames("refresh_tokens");

        assertThat(columns).containsExactlyInAnyOrder(
            "id", "user_id", "token", "expired_at", "created_at"
        );
    }

    @Test
    @DisplayName("oauth_accounts 테이블이 정상적으로 생성되어야 한다")
    void oauthAccounts_TableShouldExist() {
        assertTableExists("oauth_accounts");
    }

    @Test
    @DisplayName("oauth_accounts 테이블이 올바른 컬럼 구조를 가져야 한다")
    void oauthAccounts_TableShouldHaveCorrectColumns() {
        List<String> columns = queryColumnNames("oauth_accounts");

        assertThat(columns).containsExactlyInAnyOrder(
            "id", "user_id", "provider", "provider_id", "created_at"
        );
    }

    // ── AI 도메인 테이블 검증 ──────────────────────────────────────────────────

    @Test
    @DisplayName("ai_chat_sessions 테이블이 정상적으로 생성되어야 한다")
    void aiChatSessions_TableShouldExist() {
        assertTableExists("ai_chat_sessions");
    }

    @Test
    @DisplayName("ai_chat_sessions 테이블이 올바른 컬럼 구조를 가져야 한다")
    void aiChatSessions_TableShouldHaveCorrectColumns() {
        List<String> columns = queryColumnNames("ai_chat_sessions");

        assertThat(columns).containsExactlyInAnyOrder(
            "id", "user_id", "title", "created_at", "updated_at"
        );
    }

    @Test
    @DisplayName("ai_chat_messages 테이블이 정상적으로 생성되어야 한다")
    void aiChatMessages_TableShouldExist() {
        assertTableExists("ai_chat_messages");
    }

    @Test
    @DisplayName("ai_chat_messages 테이블이 올바른 컬럼 구조를 가져야 한다")
    void aiChatMessages_TableShouldHaveCorrectColumns() {
        List<String> columns = queryColumnNames("ai_chat_messages");

        assertThat(columns).containsExactlyInAnyOrder(
            "id", "session_id", "role", "content", "model", "created_at"
        );
    }

    // ── Spring Batch 메타데이터 테이블 검증 ──────────────────────────────────

    @Test
    @DisplayName("batch_job_instance 테이블이 정상적으로 생성되어야 한다")
    void batchJobInstance_TableShouldExist() {
        assertTableExists("batch_job_instance");
    }

    @Test
    @DisplayName("batch_job_execution 테이블이 정상적으로 생성되어야 한다")
    void batchJobExecution_TableShouldExist() {
        assertTableExists("batch_job_execution");
    }

    @Test
    @DisplayName("batch_job_execution_params 테이블이 정상적으로 생성되어야 한다")
    void batchJobExecutionParams_TableShouldExist() {
        assertTableExists("batch_job_execution_params");
    }

    @Test
    @DisplayName("batch_job_execution_context 테이블이 정상적으로 생성되어야 한다")
    void batchJobExecutionContext_TableShouldExist() {
        assertTableExists("batch_job_execution_context");
    }

    @Test
    @DisplayName("batch_step_execution 테이블이 정상적으로 생성되어야 한다")
    void batchStepExecution_TableShouldExist() {
        assertTableExists("batch_step_execution");
    }

    @Test
    @DisplayName("batch_step_execution_context 테이블이 정상적으로 생성되어야 한다")
    void batchStepExecutionContext_TableShouldExist() {
        assertTableExists("batch_step_execution_context");
    }

    // ── Spring Batch 시퀀스 검증 ──────────────────────────────────────────────

    @Test
    @DisplayName("BATCH_JOB_SEQ 시퀀스가 정상적으로 생성되어야 한다")
    void batchJobSeq_SequenceShouldExist() {
        assertSequenceExists("batch_job_seq");
    }

    @Test
    @DisplayName("BATCH_JOB_EXECUTION_SEQ 시퀀스가 정상적으로 생성되어야 한다")
    void batchJobExecutionSeq_SequenceShouldExist() {
        assertSequenceExists("batch_job_execution_seq");
    }

    @Test
    @DisplayName("BATCH_STEP_EXECUTION_SEQ 시퀀스가 정상적으로 생성되어야 한다")
    void batchStepExecutionSeq_SequenceShouldExist() {
        assertSequenceExists("batch_step_execution_seq");
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    /**
     * 지정한 테이블이 public 스키마에 존재하는지 검증합니다.
     *
     * @param tableName 검증할 테이블 이름
     */
    private void assertTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ?",
            Integer.class,
            tableName
        );

        assertThat(count)
            .as("Table '%s' should exist in public schema", tableName)
            .isEqualTo(1);
    }

    /**
     * 지정한 시퀀스가 public 스키마에 존재하는지 검증합니다.
     * PostgreSQL은 식별자를 소문자로 저장하므로 소문자로 조회합니다.
     *
     * @param sequenceName 검증할 시퀀스 이름 (대소문자 무관)
     */
    private void assertSequenceExists(String sequenceName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.sequences "
                + "WHERE sequence_schema = 'public' AND sequence_name = ?",
            Integer.class,
            sequenceName.toLowerCase()
        );

        assertThat(count)
            .as("Sequence '%s' should exist in public schema", sequenceName)
            .isEqualTo(1);
    }

    /**
     * 지정한 테이블의 컬럼 이름 목록을 조회합니다.
     *
     * @param tableName 조회할 테이블 이름
     * @return 컬럼 이름 목록 (정렬됨)
     */
    private List<String> queryColumnNames(String tableName) {
        return jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? "
                + "ORDER BY column_name",
            String.class,
            tableName
        );
    }
}
