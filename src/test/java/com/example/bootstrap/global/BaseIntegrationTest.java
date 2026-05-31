package com.example.bootstrap.global;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트 공통 베이스 (RESEARCH Pattern 4 + 싱글톤 컨테이너).
 *
 * <p>PostgreSQL 17 + Redis 7 Testcontainers를 {@code @ServiceConnection}으로 자동 배선한다.
 *
 * <p><b>싱글톤 컨테이너 패턴:</b> {@code @Testcontainers}/{@code @Container}의 클래스별 생명주기는
 * 한 테스트 클래스가 끝나면 컨테이너를 stop한다. 여러 {@code @SpringBootTest} 서브클래스가 있을 때
 * Spring 컨텍스트 캐시가 이미 중지된 컨테이너에 연결된 컨텍스트를 재사용해 "Connection refused"로
 * 실패한다. 컨테이너를 static 블록에서 한 번만 start하고 stop하지 않으면(JVM 종료 시 Ryuk이 회수)
 * 캐시된 컨텍스트의 연결이 전체 스위트 동안 유효하게 유지된다. (Rule 1 — full-suite 격리 버그 수정)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        postgres.start();
        redis.start();
    }
}
