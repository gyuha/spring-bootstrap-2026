package com.anchors.baseline;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * 공통 Testcontainers 통합 테스트 베이스.
 *
 * <p>PostgreSQL과 Redis 컨테이너를 싱글턴(static)으로 한 번만 기동해 모든 통합 테스트가
 * 단일 컨테이너 인스턴스를 재사용한다. {@code @ServiceConnection} 으로
 * Spring Boot 가 DataSource / RedisConnectionFactory 연결 정보를 자동 주입한다.
 *
 * <p>{@code @Testcontainers}/{@code @Container} 대신 싱글턴 패턴을 쓴다. 그 어노테이션은
 * 컨테이너 수명을 테스트 클래스에 묶어 클래스 종료 시 컨테이너를 중지하는데, Spring 테스트
 * 컨텍스트는 동일 설정의 여러 클래스 간에 캐시·공유되므로, 한 클래스가 끝나며 컨테이너를
 * 중지하면 캐시된 컨텍스트를 재사용하는 다음 클래스가 중지된 컨테이너에 붙어 ConnectException 이
 * 발생한다. static 초기화 블록에서 한 번 start() 하고 명시적으로 stop() 하지 않으면(JVM 종료 +
 * Ryuk 가 정리) 모든 클래스가 동일한 살아있는 컨테이너를 공유한다.
 *
 * <p>Redis 는 {@link GenericContainer} 라 타입 추론이 불가하므로
 * {@code @ServiceConnection(name = "redis")} 로 connection name 을 명시해야 한다.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }
}
