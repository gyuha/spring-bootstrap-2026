package com.example.bootstrap.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bootstrap.global.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 인증 전 흐름 통합 테스트 (D-42, AUTH-01~06) — mock 없는 실 DB(PG17)+Redis7 검증.
 *
 * <p>가입(201, 토큰 미발급) → 중복(409) → 로그인(Access+Refresh) → 보호 리소스(Bearer 200) →
 * 재발급(회전) → 옛 refresh 재제출(401, 1회용) → 재사용 탐지로 새 refresh도 무효(401, 패밀리 무효화) →
 * 로그아웃(jti 블랙리스트) → 같은 Access로 보호 리소스 재요청(401)을 한 흐름으로 박제한다.
 *
 * <p>JsonPath로 토큰을 추출한다(SB4 Jackson3 — Jackson2 ObjectMapper 빈 없음, 01-03 교훈). family/
 * blacklist Redis 상태에 의존하므로 고유 email로 격리한다(commit 6aacdf4 교훈).
 */
@AutoConfigureMockMvc
class AuthFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void fullFlow_signup_login_refresh_reuse_logout() throws Exception {
        String email = uniqueEmail();
        String password = "password1";

        // 1. 가입 → 201, 토큰 미발급 (AUTH-01)
        signup(email, password);

        // 1-b. 중복 재가입 → 409 EMAIL_ALREADY_EXISTS
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, password)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));

        // 2. 로그인 → 200, Access+Refresh 발급 (AUTH-02)
        String loginResponse = login(email, password);
        String access = JsonPath.read(loginResponse, "$.data.accessToken");
        String refresh = JsonPath.read(loginResponse, "$.data.refreshToken");
        assertThat(access).isNotBlank();
        assertThat(refresh).isNotBlank();

        // 3. 보호 리소스(GET /samples)에 Bearer Access → 200 (인증 통과)
        assertProtected(access);

        // 4. 재발급 → 200, 새 access/refresh 발급 (AUTH-03)
        String refreshResponse = refresh(refresh);
        String newAccess = JsonPath.read(refreshResponse, "$.data.accessToken");
        String newRefresh = JsonPath.read(refreshResponse, "$.data.refreshToken");
        assertThat(newAccess).isNotBlank();
        assertThat(newRefresh).isNotBlank();
        assertThat(newRefresh).isNotEqualTo(refresh); // 회전 — 옛 토큰과 다름

        // 4-b. 옛 refresh 재제출 → 401 (1회용 회전, 옛 토큰 무효)
        assertRefreshRejected(refresh);

        // 5. 재사용 탐지(AUTH-04): 옛 재제출이 패밀리를 무효화하므로, 직전 발급된 새 refresh도 401
        assertRefreshRejected(newRefresh);

        // 6. 로그아웃(AUTH-05): 유효 Access(newAccess)로 로그아웃 → jti 블랙리스트
        logout(newAccess);

        // 6-b. 같은 Access로 보호 리소스 재요청 → 401 (블랙리스트, TTL=exp-now 즉시 차단)
        mockMvc.perform(get("/samples").header("Authorization", "Bearer " + newAccess))
                .andExpect(status().isUnauthorized());
    }

    /** 테스트 간 email 충돌 방지 (commit 6aacdf4 교훈). */
    private String uniqueEmail() {
        return "u-" + UUID.randomUUID() + "@ex.com";
    }

    private String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    /** POST /auth/signup → 201, 응답 본문에 토큰 없음 (AUTH-01). */
    private void signup(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, password)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist());
    }

    /** POST /auth/login → 200, Access+Refresh 발급 (AUTH-02). 응답 본문을 반환한다. */
    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** POST /auth/refresh → 200, 회전된 Access+Refresh (AUTH-03). 응답 본문을 반환한다. */
    private String refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** POST /auth/logout (Bearer Access) → jti 블랙리스트 등록 (AUTH-05). */
    private void logout(String accessToken) throws Exception {
        mockMvc.perform(post("/auth/logout").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    /** 보호 리소스(GET /samples)에 유효 Access → 200. */
    private void assertProtected(String accessToken) throws Exception {
        mockMvc.perform(get("/samples").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    /** 무효화된 Refresh 재제출 → 401 (1회용 회전 / 재사용 탐지, AUTH-04). */
    private void assertRefreshRejected(String refreshToken) throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
