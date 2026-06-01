package com.example.bootstrap.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bootstrap.domain.user.entity.Role;
import com.example.bootstrap.global.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 사용자 관리 전 흐름 통합 테스트 (ADMIN-01~04, D-60) — mock 없는 실 PG17+Redis7 검증.
 *
 * <p>03-01/03-02가 세운 목록/단건/리셋/삭제 경로 위에서, soft-delete 한 사용자가 목록·카운트·단건
 * 양쪽에서 제외됨(P7, 삭제 포함 카운트 금지)·패스워드 리셋이 실 Redis에서 해당 사용자 Refresh를
 * 전량 무효화함(D-53)·삭제 email 재가입이 부분 유니크 인덱스로 성공함(D-56)·USER/미인증 mutation
 * 차단(D-61)을 한 스위트로 박제한다.
 *
 * <p>signup은 201만 반환하고 본문에 id가 없으므로, 특정 사용자의 실 DB id는 ADMIN 목록을 email로
 * 매칭해 추출한다(BaseIntegrationTest의 {@code bearer}는 임의 UUID라 DB row와 무관 — 주의). 다른
 * 테스트와의 데이터 간섭은 본 테스트가 생성한 사용자만 단언하거나 signup 전후 totalElements delta로
 * 검증한다. email 고유성은 UUID 접두로 보장한다.
 */
@AutoConfigureMockMvc
class AdminUserFlowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    /** 1) 삭제 제외 카운트(ADMIN-01, P7): 3명 가입 → delta=+3, 1명 삭제 → totalElements 정확히 1 감소. */
    @Test
    void softDelete_excludesFromListAndCount() throws Exception {
        long before = totalElements();

        String e1 = uniqueEmail();
        String e2 = uniqueEmail();
        String e3 = uniqueEmail();
        signup(e1);
        signup(e2);
        signup(e3);

        assertThat(totalElements()).isEqualTo(before + 3);

        // 페이징 한도 준수 + password 부재
        mockMvc.perform(get("/admin/users?page=0&size=2").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist());

        UUID id1 = idByEmail(e1);
        deleteUser(id1);

        // 삭제 포함 카운트 금지: 정확히 1 감소
        assertThat(totalElements()).isEqualTo(before + 2);
        // content에 삭제 id 부재 (큰 페이지로 전수 확인)
        String full = listAll();
        assertThat((java.util.List<?>) JsonPath.read(full, "$.data.content[?(@.id=='" + id1 + "')]"))
                .isEmpty();
    }

    /** 2) 단건 200/404 + password 부재(ADMIN-02): 존재 200, 랜덤 UUID 404, 삭제 id 404(soft-delete 제외). */
    @Test
    void getOne_existing200_missing404_deleted404() throws Exception {
        String email = uniqueEmail();
        signup(email);
        UUID id = idByEmail(email);

        mockMvc.perform(get("/admin/users/" + id).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(get("/admin/users/" + UUID.randomUUID()).header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));

        deleteUser(id);
        mockMvc.perform(get("/admin/users/" + id).header("Authorization", admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    /**
     * 3) 패스워드 리셋 + Refresh 전량 무효화(ADMIN-03, 실 Redis): A를 가입·로그인해 refresh 확보 →
     * ADMIN 리셋 → 기존 refresh로 재발급 401(invalidateAll로 패밀리 삭제) + 임시 PW로 로그인 성공.
     */
    @Test
    void passwordReset_invalidatesRefresh_andTempPasswordWorks() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String loginJson = login(email, "password1");
        String oldRefresh = JsonPath.read(loginJson, "$.data.refreshToken");

        UUID id = idByEmail(email);
        String resetJson = mockMvc.perform(
                        post("/admin/users/" + id + "/password-reset").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.temporaryPassword").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String tempPassword = JsonPath.read(resetJson, "$.data.temporaryPassword");
        assertThat(tempPassword).isNotBlank();

        // 기존 refresh 재발급 → 401 (Refresh 전량 무효화 박제)
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());

        // 임시 PW로 로그인 성공 (새 자격 동작)
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, tempPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    /** 4) soft delete + email 재가입(ADMIN-04, 부분 유니크 인덱스): 삭제 후 같은 email 재가입 201. */
    @Test
    void deletedEmail_canReSignup() throws Exception {
        String email = uniqueEmail();
        signup(email);
        UUID id = idByEmail(email);

        deleteUser(id);
        mockMvc.perform(get("/admin/users/" + id).header("Authorization", admin()))
                .andExpect(status().isNotFound());

        // ux_users_email_active가 활성만 유니크 강제 → 삭제 email 재가입 성공 (신규 마이그레이션 없음, D-59)
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "password1")))
                .andExpect(status().isCreated());
    }

    /** 5) 인가 재확인(중복 최소, D-61): USER mutation → 403, 미인증 mutation → 401. */
    @Test
    void mutationPaths_userForbidden_unauthenticatedUnauthorized() throws Exception {
        String userAccess = userLogin();
        UUID anyId = UUID.randomUUID();

        mockMvc.perform(delete("/admin/users/" + anyId).header("Authorization", "Bearer " + userAccess))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));

        mockMvc.perform(post("/admin/users/" + anyId + "/password-reset"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    // --- helpers ---

    private String admin() {
        return "Bearer " + jwtTokenProvider.issueAccess(UUID.randomUUID(), Role.ADMIN).token();
    }

    private String uniqueEmail() {
        return "f-" + UUID.randomUUID() + "@ex.com";
    }

    private String body(String email, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private void signup(String email) throws Exception {
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, "password1")))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(email, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void deleteUser(UUID id) throws Exception {
        mockMvc.perform(delete("/admin/users/" + id).header("Authorization", admin()))
                .andExpect(status().isNoContent());
    }

    private long totalElements() throws Exception {
        String json = mockMvc.perform(get("/admin/users?page=0&size=1").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return ((Number) JsonPath.read(json, "$.data.totalElements")).longValue();
    }

    /** 큰 페이지로 전체 활성 목록 JSON을 가져온다(전수 확인용). */
    private String listAll() throws Exception {
        return mockMvc.perform(get("/admin/users?page=0&size=1000").header("Authorization", admin()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    /** signup은 id를 안 주므로 ADMIN 목록을 email로 매칭해 실 DB id를 추출한다. */
    private UUID idByEmail(String email) throws Exception {
        String full = listAll();
        java.util.List<String> ids =
                JsonPath.read(full, "$.data.content[?(@.email=='" + email + "')].id");
        assertThat(ids).as("email %s should resolve to exactly one active user", email).hasSize(1);
        return UUID.fromString(ids.get(0));
    }

    /** 고유 email 가입+로그인으로 USER Access 토큰 획득(role=USER). */
    private String userLogin() throws Exception {
        String email = uniqueEmail();
        signup(email);
        String json = login(email, "password1");
        return JsonPath.read(json, "$.data.accessToken");
    }
}
