package com.example.bootstrap.account.controller;

import com.example.bootstrap.global.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("local")
@Import(TestcontainersConfig.class)
@DisplayName("AuthController 통합 테스트")
class AuthControllerIT {

    @Autowired
    private WebTestClient webTestClient;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    @Test
    @DisplayName("POST /auth/register — 신규 이메일로 회원가입 시 201과 AccountResponse를 반환한다")
    void register_withNewEmail_returns201AndAccountResponse() {
        long ts = System.currentTimeMillis();
        String body = String.format(
                "{\"email\":\"reg_%d@example.com\",\"password\":\"pass1234\",\"nickname\":\"NewUser\"}", ts);

        webTestClient.post().uri(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUCCESS")
                .jsonPath("$.data.email").isEqualTo("reg_" + ts + "@example.com")
                .jsonPath("$.data.nickname").isEqualTo("NewUser")
                .jsonPath("$.data.role").isEqualTo("USER");
    }

    @Test
    @DisplayName("POST /auth/register — 중복 이메일이면 409 Conflict를 반환한다")
    void register_withDuplicateEmail_returns409() {
        long ts = System.currentTimeMillis();
        String body = String.format(
                "{\"email\":\"dup_%d@example.com\",\"password\":\"pass1234\",\"nickname\":\"User1\"}", ts);

        webTestClient.post().uri(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated();

        webTestClient.post().uri(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("POST /auth/login — 올바른 자격증명으로 로그인 시 200과 TokenResponse를 반환한다")
    void login_withValidCredentials_returns200AndTokenResponse() {
        long ts = System.currentTimeMillis();
        String registerBody = String.format(
                "{\"email\":\"login_%d@example.com\",\"password\":\"pass1234\",\"nickname\":\"LoginUser\"}", ts);
        webTestClient.post().uri(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody)
                .exchange()
                .expectStatus().isCreated();

        String loginBody = String.format(
                "{\"email\":\"login_%d@example.com\",\"password\":\"pass1234\"}", ts);
        webTestClient.post().uri(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUCCESS")
                .jsonPath("$.data.accessToken").isNotEmpty()
                .jsonPath("$.data.refreshToken").isNotEmpty();
    }

    @Test
    @DisplayName("POST /auth/login — 틀린 비밀번호이면 401을 반환한다")
    void login_withWrongPassword_returns401() {
        long ts = System.currentTimeMillis();
        String registerBody = String.format(
                "{\"email\":\"wrong_%d@example.com\",\"password\":\"correct\",\"nickname\":\"WrongUser\"}", ts);
        webTestClient.post().uri(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody)
                .exchange()
                .expectStatus().isCreated();

        String loginBody = String.format(
                "{\"email\":\"wrong_%d@example.com\",\"password\":\"incorrect\"}", ts);
        webTestClient.post().uri(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginBody)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("POST /auth/refresh — 유효한 Refresh Token으로 새 토큰을 발급받는다")
    void refresh_withValidRefreshToken_returnsNewTokenResponse() {
        long ts = System.currentTimeMillis();
        String email = "refresh_" + ts + "@example.com";

        String registerBody = String.format(
                "{\"email\":\"%s\",\"password\":\"pass1234\",\"nickname\":\"RefreshUser\"}", email);
        webTestClient.post().uri(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody)
                .exchange()
                .expectStatus().isCreated();

        String loginBody = String.format("{\"email\":\"%s\",\"password\":\"pass1234\"}", email);
        AtomicReference<String> refreshToken = new AtomicReference<>();
        webTestClient.post().uri(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.refreshToken").value(rt -> refreshToken.set((String) rt));

        String refreshBody = String.format("{\"refreshToken\":\"%s\"}", refreshToken.get());
        webTestClient.post().uri(REFRESH_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(refreshBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.accessToken").isNotEmpty()
                .jsonPath("$.data.refreshToken").isNotEmpty();
    }

    @Test
    @DisplayName("POST /auth/logout — 유효한 Access Token으로 로그아웃 시 200을 반환한다")
    void logout_withValidToken_returns200() {
        long ts = System.currentTimeMillis();
        String email = "logout_" + ts + "@example.com";

        String registerBody = String.format(
                "{\"email\":\"%s\",\"password\":\"pass1234\",\"nickname\":\"LogoutUser\"}", email);
        webTestClient.post().uri(REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(registerBody)
                .exchange()
                .expectStatus().isCreated();

        String loginBody = String.format("{\"email\":\"%s\",\"password\":\"pass1234\"}", email);
        AtomicReference<String> accessToken = new AtomicReference<>();
        AtomicReference<String> refreshToken = new AtomicReference<>();
        webTestClient.post().uri(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.accessToken").value(at -> accessToken.set((String) at))
                .jsonPath("$.data.refreshToken").value(rt -> refreshToken.set((String) rt));

        String logoutBody = String.format("{\"refreshToken\":\"%s\"}", refreshToken.get());
        webTestClient.post().uri(LOGOUT_URL)
                .header("Authorization", "Bearer " + accessToken.get())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(logoutBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.code").isEqualTo("SUCCESS");
    }
}
