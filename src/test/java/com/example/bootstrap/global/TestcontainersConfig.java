package com.example.bootstrap.global;

import com.redis.testcontainers.RedisContainer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 TestContainers 설정.
 *
 * <p>PostgreSQL과 Redis 컨테이너를 자동으로 시작하고
 * Spring Boot 자동 구성과 연결합니다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /**
     * PostgreSQL TestContainer Bean.
     *
     * <p>{@link ServiceConnection}을 통해 R2DBC 및 JDBC DataSource에 자동 연결됩니다.
     *
     * @return {@link PostgreSQLContainer} 인스턴스
     */
    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("bootstrap_test")
            .withUsername("bootstrap")
            .withPassword("bootstrap");
    }

    /**
     * Redis TestContainer Bean.
     *
     * <p>{@link ServiceConnection}을 통해 Redis 연결에 자동 연결됩니다.
     *
     * @return {@link RedisContainer} 인스턴스
     */
    @Bean
    @ServiceConnection
    public RedisContainer redisContainer() {
        return new RedisContainer(DockerImageName.parse("redis:7-alpine"));
    }
}
