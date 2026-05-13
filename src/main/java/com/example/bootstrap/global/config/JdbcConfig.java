package com.example.bootstrap.global.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * JDBC DataSource 및 JdbcTemplate 명시적 구성.
 *
 * <p>WebFlux + R2DBC 환경에서 {@code DataSourceAutoConfiguration}이
 * {@code ConnectionFactory} 빈의 존재로 인해 DataSource 빈을 자동 구성하지
 * 않을 수 있습니다. 이 설정은 Flyway 마이그레이션 검증, Spring Batch
 * JobRepository에 필요한 JDBC {@code DataSource}와 {@code JdbcTemplate}을
 * 명시적으로 등록합니다.</p>
 *
 * <p>TestContainers {@code @ServiceConnection} 환경에서는 {@link JdbcConnectionDetails}
 * 빈이 자동 등록되며, 이 설정은 해당 빈을 우선적으로 사용합니다.
 * 프로덕션 환경에서는 {@code spring.datasource.*} 프로퍼티를 사용합니다.</p>
 */
@Configuration
public class JdbcConfig {

    @Bean
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(
            @Autowired(required = false) JdbcConnectionDetails connectionDetails,
            @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/bootstrap}")
                String jdbcUrl,
            @Value("${spring.datasource.username:bootstrap}") String username,
            @Value("${spring.datasource.password:bootstrap}") String password) {
        HikariConfig config = new HikariConfig();
        if (connectionDetails != null) {
            config.setJdbcUrl(connectionDetails.getJdbcUrl());
            config.setUsername(connectionDetails.getUsername());
            config.setPassword(connectionDetails.getPassword());
            config.setDriverClassName(connectionDetails.getDriverClassName());
        } else {
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
        }
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000L);
        return new HikariDataSource(config);
    }

    @Bean
    @ConditionalOnMissingBean(JdbcTemplate.class)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
