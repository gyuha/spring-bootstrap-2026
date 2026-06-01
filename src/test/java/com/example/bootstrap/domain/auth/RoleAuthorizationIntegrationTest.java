package com.example.bootstrap.domain.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bootstrap.domain.auth.support.AdminOnlyTestController;
import com.example.bootstrap.domain.user.entity.Role;
import com.example.bootstrap.global.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 경로/메서드 인가 통합 검증 — 401/403/200 + Bearer 프리픽스 회귀 가드 (AUTH-06, D-30/D-38).
 *
 * <p>I-2: 미인증 GET {@code /samples} → 401(필수·비선택). secure-by-default 전환이 매처 오순서로
 * 조용히 깨지는 회귀를 핀으로 막는다.
 *
 * <p>I-3: {@code AdminOnlyTestController}(@PreAuthorize hasRole ADMIN)를 {@code @Import}로 명시
 * 로딩한다(컴포넌트 스캔 모호성 제거). USER→403, ADMIN→200은 02-03의 {@code @EnableMethodSecurity}에
 * 의존한다.
 *
 * <p>토큰 획득: USER는 실제 가입+로그인으로(role=USER, D-33), ADMIN은 {@link com.example.bootstrap
 * .global.security.JwtTokenProvider}로 {@link Role#ADMIN} Access를 직접 발급한다(ADMIN 부여 경로는
 * Phase 2 범위 외). claim {@code role}=ADMIN → {@code ROLE_ADMIN} authority 매핑을 검증한다.
 */
@AutoConfigureMockMvc
@Import(AdminOnlyTestController.class)
class RoleAuthorizationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    /** I-2 (필수·비선택): 미인증 보호 리소스 접근 → 401 ProblemDetail. */
    @Test
    void unauthenticated_protectedResource_returns401() throws Exception {
        mockMvc.perform(get("/samples"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    /** USER 토큰으로 ADMIN 전용 경로 → 403 ProblemDetail (권한 부족, @EnableMethodSecurity). */
    @Test
    void userToken_adminOnly_returns403() throws Exception {
        String userAccess = userLogin();

        mockMvc.perform(get("/test/admin-only").header("Authorization", "Bearer " + userAccess))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    /** ADMIN 토큰으로 ADMIN 전용 경로 → 200 (role=ADMIN → ROLE_ADMIN 매핑). */
    @Test
    void adminToken_adminOnly_returns200() throws Exception {
        String adminAccess = jwtTokenProvider.issueAccess(UUID.randomUUID(), Role.ADMIN).token();

        mockMvc.perform(get("/test/admin-only").header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    /** USER 토큰으로 경로 기반 ADMIN 전용 {@code /admin/**} → 403 (D-45/D-61, Phase 3 인가 회귀 가드). */
    @Test
    void userToken_adminPath_returns403() throws Exception {
        String userAccess = userLogin();

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + userAccess))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
    }

    /** 미인증으로 {@code /admin/**} 접근 → 401 (D-45/D-61). */
    @Test
    void unauthenticated_adminPath_returns401() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    /** ADMIN 토큰으로 {@code /admin/**} → 200 (role=ADMIN → ROLE_ADMIN 매핑, D-45/D-61). */
    @Test
    void adminToken_adminPath_returns200() throws Exception {
        String adminAccess = jwtTokenProvider.issueAccess(UUID.randomUUID(), Role.ADMIN).token();

        mockMvc.perform(get("/admin/users").header("Authorization", "Bearer " + adminAccess))
                .andExpect(status().isOk());
    }

    /** Bearer 프리픽스 없는 헤더 → 401 (회귀 가드, D-30, commit 6aacdf4 박제). */
    @Test
    void missingBearerPrefix_returns401() throws Exception {
        String userAccess = userLogin();

        // "Bearer " 프리픽스 없이 토큰만 전달 — 인증 실패해야 한다
        mockMvc.perform(get("/samples").header("Authorization", userAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    /** 고유 email로 가입 후 로그인해 USER Access 토큰을 획득한다(role=USER, D-33). */
    private String userLogin() throws Exception {
        String email = "u-" + UUID.randomUUID() + "@ex.com";
        String password = "password1";
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.data.accessToken");
    }
}
