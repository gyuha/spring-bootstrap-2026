package com.example.bootstrap.account.controller;

import com.example.bootstrap.global.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

/**
 * AccountController 통합 테스트.
 *
 * <p>TestContainers PostgreSQL + Redis를 사용하여 전체 스택을 구동합니다.
 * 내 정보 조회, 프로필 수정, 회원 탈퇴 흐름을 End-to-End로 검증합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@Import(TestcontainersConfig.class)
@DisplayName("AccountController 통합 테스트")
class AccountControllerIT {

    @Autowired
    private WebTestClient webTestClient;

    private String accessToken;
    private String testEmail;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        testEmail = "acct_" + uid + "@example.com";

        String registerBody = String.format(
                "{\"email\":\"%s\",\"password\":\"pass1234\",\"nickname\":\"TestUser\"}", testEmail);
        webTestClient.post().uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody)
                .exchange()
                .expectStatus().isCreated();

        String loginBody = String.format(
                "{\"email\":\"%s\",\"password\":\"pass1234\"}", testEmail);
        webTestClient.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.accessToken").value(token -> this.accessToken = (String) token);
    }

    @Test
    @DisplayName("GET /accounts/me — 유효한 Bearer 토큰으로 내 정보를 조회한다")
    void getMe_withValidToken_returns200AndAccountResponse() {
        webTestClient.get().uri("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUCCESS")
                .jsonPath("$.data.id").isNotEmpty()
                .jsonPath("$.data.email").isEqualTo(testEmail)
                .jsonPath("$.data.nickname").isEqualTo("TestUser");
    }

    @Test
    @DisplayName("GET /accounts/me — 토큰 없이 요청하면 401을 반환한다")
    void getMe_withoutToken_returns401() {
        webTestClient.get().uri("/api/v1/accounts/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("PUT /accounts/me — 유효한 토큰으로 프로필을 수정한다")
    void updateMe_withValidToken_returns200AndUpdatedResponse() {
        String updateBody = "{\"nickname\":\"UpdatedNick\",\"profileImageUrl\":\"https://example.com/img.jpg\"}";

        webTestClient.put().uri("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUCCESS")
                .jsonPath("$.data.nickname").isEqualTo("UpdatedNick")
                .jsonPath("$.data.profileImageUrl").isEqualTo("https://example.com/img.jpg");
    }

    @Test
    @DisplayName("DELETE /accounts/me — 유효한 토큰으로 탈퇴 시 200을 반환한다")
    void deleteMe_withValidToken_returns200() {
        webTestClient.delete().uri("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("DELETE /accounts/me 후 다시 GET /accounts/me 요청하면 401을 반환한다 (Access Token 블랙리스트)")
    void deleteMe_thenGetMe_returns401() {
        webTestClient.delete().uri("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isOk();

        webTestClient.get().uri("/api/v1/accounts/me")
                .header("Authorization", "Bearer " + accessToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
