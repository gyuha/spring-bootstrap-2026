package com.anchors.baseline.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.authorization.application.AuthorizationApplicationService;
import com.anchors.baseline.authorization.domain.model.RoleName;
import com.anchors.baseline.identity.application.IdentityApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Phase 6 내 정보 신원·권한 집계 SC#1~3 행위 단언(AbstractIntegrationTest 상속 — Testcontainers Postgres+Redis).
 *
 * <ul>
 *   <li>SC#1 신원 조회(200)/비인증(401), SC#3 permitAll 회귀 — MockMvc + {@code oidcLogin()} post-processor.
 *   <li>SC#2 권한 단일 응답 결합 — 실 Postgres 에 grant 를 직접 시드(authorizationService)하고 me() 응답을 단언.
 * </ul>
 *
 * <p>3자 일치(D-07): {@code identityService.invite(email)} 가 반환한 실 DB PK 를 {@code oidcLogin} claim
 * {@code user_id} 와 grant 시드 userId 에 모두 사용한다 — 합성 userId 하드코딩 금지(lesson 05)로 집계 거짓 통과 차단.
 * 권한은 직접 부여(direct)만 단언 — D-02.
 */
@AutoConfigureMockMvc
class BffAuthMeIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityApplicationService identityService;

    @Autowired
    AuthorizationApplicationService authorizationService;

    // ===================== SC#1: 신원 조회 + 비인증 401 =====================

    @Test
    void meReturnsIdentity() throws Exception {
        String email = uniqueEmail("sc1");
        Long seededUserId = identityService.invite(email);

        mvc.perform(get("/api/auth/me")
                        .with(oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(seededUserId.intValue()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.status").value("INVITED"));
    }

    @Test
    void meUnauthenticatedReturns401() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    // ===================== SC#3: permitAll 매처 회귀 (me는 보호, session은 공개) =====================

    @Test
    void meIsNotPermitAll() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session")).andExpect(status().isOk());
    }

    // ===================== SC#2: 신원 + 직접 부여 권한 단일 응답 결합 =====================

    @Test
    void meReturnsGrantedPermissions() throws Exception {
        String email = uniqueEmail("sc2");
        Long seededUserId = identityService.invite(email);
        authorizationService.grantGlobalRole(seededUserId, RoleName.ADMIN);
        authorizationService.grantMenuToUser(seededUserId, 1L, RoleName.EDITOR);
        authorizationService.grantResourceToUser(seededUserId, 10L, RoleName.VIEWER);

        mvc.perform(get("/api/auth/me")
                        .with(oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(seededUserId.intValue()))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.menus[0].menuId").value(1))
                .andExpect(jsonPath("$.menus[0].role").value("EDITOR"))
                .andExpect(jsonPath("$.resources[0].resourceId").value(10))
                .andExpect(jsonPath("$.resources[0].role").value("VIEWER"));
    }

    @Test
    void meReturnsEmptyPermissionsForNewUser() throws Exception {
        String email = uniqueEmail("sc2-empty");
        Long seededUserId = identityService.invite(email);

        mvc.perform(get("/api/auth/me")
                        .with(oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.menus").isArray())
                .andExpect(jsonPath("$.menus").isEmpty())
                .andExpect(jsonPath("$.resources").isArray())
                .andExpect(jsonPath("$.resources").isEmpty());
    }

    /** 환경 종속 하드코딩 금지(lesson 05) — 테스트 간 격리를 위한 고유 이메일. */
    private static String uniqueEmail(String tag) {
        return tag + "-" + System.nanoTime() + "@example.com";
    }
}
