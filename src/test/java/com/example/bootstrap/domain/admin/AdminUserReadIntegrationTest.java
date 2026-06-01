package com.example.bootstrap.domain.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bootstrap.domain.user.entity.Role;
import com.example.bootstrap.global.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 관리자 사용자 읽기 경로 통합 검증 — 목록 페이징/단건/404 + 인가 403/401 (ADMIN-01/02, D-60).
 *
 * <p>실제 Testcontainers(PG17+Redis7)에서 {@code /auth/signup}으로 활성 사용자를 만들고 ADMIN
 * 토큰({@code jwtTokenProvider.issueAccess(.., Role.ADMIN)})으로 조회한다. soft-delete 제외
 * 검증은 삭제 경로가 있는 03-03에 위임하고, 여기서는 활성 사용자만 카운트/목록 노출 + 페이징
 * 정확 + 단건 200/404 + 인가 403/401 + password 부재를 박제한다.
 */
@AutoConfigureMockMvc
class AdminUserReadIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private String adminAccess() {
        return jwtTokenProvider.issueAccess(UUID.randomUUID(), Role.ADMIN).token();
    }

    /** 고유 email로 가입해 활성 사용자를 만든다. */
    private void signup(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"password1\"}";
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    /** ADMIN 목록 페이징 — size 한도 준수 + totalElements>=가입수 + password 부재. */
    @Test
    void adminList_paginates_andHidesPassword() throws Exception {
        signup("a-" + UUID.randomUUID() + "@ex.com");
        signup("a-" + UUID.randomUUID() + "@ex.com");
        signup("a-" + UUID.randomUUID() + "@ex.com");

        mockMvc.perform(get("/admin/users?page=0&size=2")
                        .header("Authorization", "Bearer " + adminAccess()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(2))
                .andExpect(jsonPath("$.data.totalElements", Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.content[0].password").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].email").exists())
                .andExpect(jsonPath("$.data.content[0].role").exists());
    }

    /** ADMIN 단건 — 가입 후 목록 첫 항목 id로 GET → 200 email 일치. */
    @Test
    void adminGetOne_existing_returns200() throws Exception {
        String email = "a-" + UUID.randomUUID() + "@ex.com";
        signup(email);

        String listJson = mockMvc.perform(get("/admin/users?page=0&size=1")
                        .header("Authorization", "Bearer " + adminAccess()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String id = JsonPath.read(listJson, "$.data.content[0].id");

        mockMvc.perform(get("/admin/users/" + id)
                        .header("Authorization", "Bearer " + adminAccess()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    /** ADMIN 단건 — 없는 id → 404 USER_NOT_FOUND ProblemDetail. */
    @Test
    void adminGetOne_missing_returns404() throws Exception {
        mockMvc.perform(get("/admin/users/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminAccess()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }
}
