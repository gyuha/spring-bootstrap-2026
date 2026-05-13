package com.example.bootstrap.global;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Actuator /health 엔드포인트 통합 테스트.
 *
 * <p>TestContainers PostgreSQL + Redis를 사용하여 전체 스택을 구동 후
 * /actuator/health가 200 OK를 반환하는지 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@Import(TestcontainersConfig.class)
@DisplayName("Actuator Health 엔드포인트 통합 테스트")
class ActuatorHealthIT {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("/actuator/health 요청 시 200 OK와 UP 상태를 반환해야 한다")
    void actuatorHealth_ShouldReturn200Ok() {
        webTestClient.get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("/actuator/health/liveness는 200 OK를 반환해야 한다")
    void actuatorHealthLiveness_ShouldReturn200Ok() {
        webTestClient.get()
            .uri("/actuator/health/liveness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("/actuator/health/readiness는 200 OK를 반환해야 한다")
    void actuatorHealthReadiness_ShouldReturn200Ok() {
        webTestClient.get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.status").isEqualTo("UP");
    }
}
