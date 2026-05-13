package com.example.bootstrap.batch.application.job;

import com.example.bootstrap.global.TestcontainersConfig;
import java.time.LocalDateTime;
import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 만료 Refresh Token 정리 배치 잡 슬라이스 테스트.
 *
 * <p>{@link SpringBatchTest} 슬라이스 어노테이션으로 {@link JobLauncherTestUtils}와
 * {@link JobRepositoryTestUtils}를 자동 구성합니다. TestContainers PostgreSQL을 사용하여
 * 실제 DB 환경에서 Job/Step 단위 실행 결과와 {@link ExitStatus}를 검증합니다.</p>
 *
 * <p>컨텍스트 구성:</p>
 * <ul>
 *   <li>{@code @SpringBatchTest} — JobLauncherTestUtils, JobRepositoryTestUtils 제공</li>
 *   <li>{@code @SpringBootTest(webEnvironment = NONE)} — 전체 Spring 컨텍스트 (HTTP 서버 제외)</li>
 *   <li>{@code @Import(TestcontainersConfig)} — PostgreSQL + Redis TestContainers</li>
 *   <li>{@code @ActiveProfiles("local")} — local 프로파일 설정 적용</li>
 * </ul>
 *
 * <p>주의: 이 클래스에는 {@link StepExecution}을 반환하는 메서드를 정의하지 않습니다.
 * {@code StepScopeTestExecutionListener}가 반환 타입 기반으로 스텝 실행 메서드를
 * 스캔하여 인자 없이 호출을 시도하기 때문입니다.</p>
 */
@SpringBatchTest
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfig.class)
@ActiveProfiles("local")
@DisplayName("만료 Refresh Token 정리 배치 잡 슬라이스 테스트")
class ExpiredTokenCleanupJobTest {

    private static final String INSERT_USER_SQL =
        "INSERT INTO users (email, nickname, role, email_verified) "
            + "VALUES (?, 'BatchTestUser', 'USER', TRUE)";

    private static final String SELECT_USER_ID_SQL =
        "SELECT id FROM users WHERE email = ?";

    private static final String INSERT_TOKEN_SQL =
        "INSERT INTO refresh_tokens (user_id, token, expired_at) VALUES (?, ?, ?)";

    private static final String COUNT_TOKENS_SQL =
        "SELECT COUNT(*) FROM refresh_tokens";

    private static final String SELECT_TOKEN_SQL =
        "SELECT token FROM refresh_tokens";

    private static final String DELETE_TEST_USERS_SQL =
        "DELETE FROM users WHERE email LIKE 'batch-test-%'";

    private static final String DELETE_ALL_TOKENS_SQL =
        "DELETE FROM refresh_tokens";

    private static final String STEP_NAME = "deleteExpiredTokensStep";

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 각 테스트 전 배치 메타데이터와 테스트 데이터를 초기화합니다.
     *
     * <p>순서: refresh_tokens 삭제(FK 제약 준수) → 테스트 users 삭제 → 배치 실행 이력 삭제</p>
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.update(DELETE_ALL_TOKENS_SQL);
        jdbcTemplate.update(DELETE_TEST_USERS_SQL);
        jobRepositoryTestUtils.removeJobExecutions();
    }

    // ── Job 레벨 테스트 ───────────────────────────────────────────────────────

    @Test
    @DisplayName("토큰이 없을 때 잡이 COMPLETED 상태로 완료되어야 한다")
    void job_withNoTokens_shouldCompleteSuccessfully() throws Exception {
        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // then — Job 수준 상태 검증
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("만료된 토큰이 있을 때 잡이 COMPLETED 상태로 완료되어야 한다")
    void job_withExpiredTokens_shouldCompleteWithCompletedStatus() throws Exception {
        // given
        long userId = insertTestUser("batch-test-job@example.com");
        insertExpiredToken(userId, "expired-job-token-1");
        insertExpiredToken(userId, "expired-job-token-2");

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // then — Job 상태 및 ExitStatus 검증
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("잡 실행 후 만료 토큰이 DB에서 삭제되어야 한다")
    void job_afterExecution_expiredTokensShouldBeDeletedFromDb() throws Exception {
        // given
        long userId = insertTestUser("batch-test-delete@example.com");
        insertExpiredToken(userId, "to-be-deleted-token");

        assertThat(countTokens()).isEqualTo(1);

        // when
        jobLauncherTestUtils.launchJob();

        // then — DB 상태 검증
        assertThat(countTokens()).isZero();
    }

    // ── Step 레벨 테스트 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteExpiredTokensStep이 COMPLETED ExitStatus를 반환해야 한다")
    void step_shouldReturnCompletedExitStatus() throws Exception {
        // given
        long userId = insertTestUser("batch-test-step@example.com");
        insertExpiredToken(userId, "step-test-expired-token");

        // when — Step 단독 실행
        JobExecution jobExecution = jobLauncherTestUtils.launchStep(STEP_NAME);

        // then — Step 수준 ExitStatus 검증 (StepExecution 반환 타입 메서드 회피를 위해 인라인 추출)
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);
        StepExecution stepExecution = stepExecutions.iterator().next();

        assertThat(stepExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(stepExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    @DisplayName("만료 토큰 수만큼 writeCount가 StepExecution에 기록되어야 한다")
    void step_shouldRecordWriteCountMatchingDeletedTokenCount() throws Exception {
        // given — 만료 토큰 3건
        long userId = insertTestUser("batch-test-count@example.com");
        insertExpiredToken(userId, "expired-count-token-a");
        insertExpiredToken(userId, "expired-count-token-b");
        insertExpiredToken(userId, "expired-count-token-c");

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchStep(STEP_NAME);

        // then — writeCount = 삭제된 토큰 수 검증
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);
        StepExecution stepExecution = stepExecutions.iterator().next();

        assertThat(stepExecution.getWriteCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("만료 토큰만 삭제되고 유효한 토큰은 보존되어야 한다")
    void step_shouldDeleteOnlyExpiredTokensAndPreserveValidOnes() throws Exception {
        // given — 만료 토큰 1건 + 유효 토큰 1건
        long userId = insertTestUser("batch-test-preserve@example.com");
        insertExpiredToken(userId, "old-expired-token");
        insertValidToken(userId, "still-valid-token");

        assertThat(countTokens()).isEqualTo(2);

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchStep(STEP_NAME);

        // then — 만료 토큰 1건 삭제, 유효 토큰 1건 보존
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);
        StepExecution stepExecution = stepExecutions.iterator().next();

        assertThat(stepExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(stepExecution.getWriteCount()).isEqualTo(1);

        assertThat(countTokens()).isEqualTo(1);
        String remainingToken = jdbcTemplate.queryForObject(SELECT_TOKEN_SQL, String.class);
        assertThat(remainingToken).isEqualTo("still-valid-token");
    }

    @Test
    @DisplayName("토큰이 없을 때 Step의 writeCount는 0이어야 한다")
    void step_withNoExpiredTokens_writeCountShouldBeZero() throws Exception {
        // when — 토큰 없이 Step 실행
        JobExecution jobExecution = jobLauncherTestUtils.launchStep(STEP_NAME);

        // then
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);
        StepExecution stepExecution = stepExecutions.iterator().next();

        assertThat(stepExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(stepExecution.getWriteCount()).isZero();
    }

    @Test
    @DisplayName("유효한 토큰만 있을 때 Step이 정상 완료되고 writeCount는 0이어야 한다")
    void step_withOnlyValidTokens_shouldCompleteWithZeroWriteCount() throws Exception {
        // given — 유효 토큰만 존재
        long userId = insertTestUser("batch-test-valid@example.com");
        insertValidToken(userId, "valid-token-only");

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchStep(STEP_NAME);

        // then
        Collection<StepExecution> stepExecutions = jobExecution.getStepExecutions();
        assertThat(stepExecutions).hasSize(1);
        StepExecution stepExecution = stepExecutions.iterator().next();

        assertThat(stepExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        assertThat(stepExecution.getWriteCount()).isZero();
        assertThat(countTokens()).isEqualTo(1);
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    /**
     * 테스트용 사용자를 {@code users} 테이블에 삽입하고 생성된 PK를 반환합니다.
     *
     * @param email 고유 이메일 주소
     * @return 생성된 사용자 PK
     */
    private long insertTestUser(final String email) {
        jdbcTemplate.update(INSERT_USER_SQL, email);
        Long id = jdbcTemplate.queryForObject(SELECT_USER_ID_SQL, Long.class, email);
        assertThat(id).isNotNull();
        return id;
    }

    /**
     * 만료된 Refresh Token을 삽입합니다 (expired_at = 현재 시각 기준 1일 전).
     *
     * @param userId 사용자 PK
     * @param token  토큰 값
     */
    private void insertExpiredToken(final long userId, final String token) {
        jdbcTemplate.update(INSERT_TOKEN_SQL, userId, token, LocalDateTime.now().minusDays(1));
    }

    /**
     * 유효한 Refresh Token을 삽입합니다 (expired_at = 현재 시각 기준 14일 후).
     *
     * @param userId 사용자 PK
     * @param token  토큰 값
     */
    private void insertValidToken(final long userId, final String token) {
        jdbcTemplate.update(INSERT_TOKEN_SQL, userId, token, LocalDateTime.now().plusDays(14));
    }

    /**
     * {@code refresh_tokens} 테이블의 전체 행 수를 반환합니다.
     *
     * @return 현재 토큰 수
     */
    private int countTokens() {
        Integer count = jdbcTemplate.queryForObject(COUNT_TOKENS_SQL, Integer.class);
        return count != null ? count : 0;
    }
}
