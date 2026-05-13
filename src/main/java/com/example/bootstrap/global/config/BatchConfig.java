package com.example.bootstrap.global.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch JDBC 트랜잭션 매니저 명시적 구성.
 *
 * <p>WebFlux + R2DBC 환경에서는 {@code R2dbcTransactionManager}(ReactiveTransactionManager)만
 * 자동 구성되며, {@code PlatformTransactionManager}를 기대하는
 * {@link org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration}이
 * 조건부 체크({@code @ConditionalOnBean(PlatformTransactionManager.class)})를 통과하지
 * 못해 {@code JobRepository}와 {@code JobLauncher}가 등록되지 않는 문제가 발생합니다.</p>
 *
 * <p>이 설정은 JDBC {@link DataSource}를 사용하는 {@code DataSourceTransactionManager}를
 * 명시적으로 {@code batchTransactionManager}로 등록하여 Spring Batch 자동 구성이
 * 정상 동작하도록 합니다. R2DBC 리액티브 트랜잭션과는 독립적으로 동작합니다.</p>
 */
@Configuration
public class BatchConfig {

    /**
     * Spring Batch 전용 JDBC 트랜잭션 매니저 빈을 등록합니다.
     *
     * <p>이 빈이 존재하면:
     * <ol>
     *   <li>{@code BatchAutoConfiguration}의 {@code @ConditionalOnBean(PlatformTransactionManager.class)}
     *       조건이 충족되어 {@code JobRepository}, {@code JobLauncher} 자동 구성이 실행됩니다.</li>
     *   <li>{@code BatchAutoConfiguration.BatchTransactionManagerConfiguration}의
     *       {@code @ConditionalOnMissingBean(name = "batchTransactionManager")} 조건이
     *       불충족되어 중복 생성이 방지됩니다.</li>
     * </ol>
     * </p>
     *
     * @param dataSource JDBC DataSource (JdbcConfig 또는 Spring Boot 자동 구성이 제공)
     * @return JDBC 기반 {@link PlatformTransactionManager}
     */
    @Bean("batchTransactionManager")
    public PlatformTransactionManager batchTransactionManager(final DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
