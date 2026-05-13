package com.example.bootstrap.batch.application.job;

import java.time.LocalDateTime;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 만료 Refresh Token 정리 배치 잡 설정.
 *
 * <p>WebFlux 리액티브 런타임과 격리된 blocking JDBC 스레드에서 실행되며,
 * {@code refresh_tokens} 테이블에서 만료된 토큰을 일괄 삭제합니다.
 * Spring Boot 자동 설정이 제공하는 {@code batchTransactionManager}를 사용하여
 * JDBC 트랜잭션 경계를 관리합니다.</p>
 *
 * <p>잡 구조:</p>
 * <pre>
 *   expiredTokenCleanupJob
 *     └── deleteExpiredTokensStep (Tasklet)
 *           └── DELETE FROM refresh_tokens WHERE expired_at &lt; NOW()
 * </pre>
 */
@Configuration
public class ExpiredTokenCleanupJobConfig {

    /** 삭제할 만료 토큰 SQL. */
    static final String DELETE_EXPIRED_TOKENS_SQL =
        "DELETE FROM refresh_tokens WHERE expired_at < ?";

    /**
     * 만료 토큰 정리 Job 빈을 생성합니다.
     *
     * @param jobRepository  Spring Batch Job 메타데이터 저장소
     * @param deleteExpiredTokensStep 만료 토큰 삭제 Step
     * @return 설정된 {@link Job} 인스턴스
     */
    @Bean
    public Job expiredTokenCleanupJob(
            final JobRepository jobRepository,
            final Step deleteExpiredTokensStep) {
        return new JobBuilder("expiredTokenCleanupJob", jobRepository)
            .start(deleteExpiredTokensStep)
            .build();
    }

    /**
     * 만료 토큰 삭제 Step 빈을 생성합니다.
     *
     * <p>{@code batchTransactionManager}는 Spring Boot Batch 자동 설정이 등록하는
     * JDBC 전용 {@link org.springframework.jdbc.datasource.DataSourceTransactionManager}입니다.
     * R2DBC 트랜잭션 매니저와 충돌하지 않도록 명시적으로 한정합니다.</p>
     *
     * @param jobRepository      Spring Batch Job 메타데이터 저장소
     * @param transactionManager JDBC 전용 트랜잭션 매니저 (batchTransactionManager)
     * @param jdbcTemplate       JDBC 쿼리 실행 템플릿
     * @return 설정된 {@link Step} 인스턴스
     */
    @Bean
    public Step deleteExpiredTokensStep(
            final JobRepository jobRepository,
            @Qualifier("batchTransactionManager") final PlatformTransactionManager transactionManager,
            final JdbcTemplate jdbcTemplate) {
        return new StepBuilder("deleteExpiredTokensStep", jobRepository)
            .tasklet(deleteExpiredTokensTasklet(jdbcTemplate), transactionManager)
            .build();
    }

    /**
     * 만료 토큰 삭제 Tasklet 빈을 생성합니다.
     *
     * <p>현재 시각({@link LocalDateTime#now()})을 기준으로 {@code expired_at} 컬럼이
     * 과거인 레코드를 삭제하고, 삭제된 행 수를 {@code writeCount}로 기록합니다.</p>
     *
     * @param jdbcTemplate JDBC 쿼리 실행 템플릿
     * @return 만료 토큰 삭제 {@link Tasklet}
     */
    @Bean
    public Tasklet deleteExpiredTokensTasklet(final JdbcTemplate jdbcTemplate) {
        return (contribution, chunkContext) -> {
            int deleted = jdbcTemplate.update(DELETE_EXPIRED_TOKENS_SQL, LocalDateTime.now());
            contribution.incrementWriteCount(deleted);
            return RepeatStatus.FINISHED;
        };
    }
}
