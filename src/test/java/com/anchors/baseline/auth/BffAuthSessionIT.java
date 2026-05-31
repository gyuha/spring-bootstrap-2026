package com.anchors.baseline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.auth.support.MockOidcServer;
import com.anchors.baseline.identity.application.IdentityApplicationService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Phase 5 인증 웹 인터페이스 SC#1~4 행위 단언(AbstractIntegrationTest 상속 — Testcontainers Postgres+Redis).
 *
 * <ul>
 *   <li>SC#1 session 200 분기 / SC#4 permitAll 매처 — 상태만 단언하므로 MockMvc + oidcLogin().
 *   <li>SC#2 logout 204 + Redis 세션 삭제 / SC#3 login 302 + returnTo 복귀 — 서버측 세션 read-back·실
 *       토큰 교환이 필요하므로 WireMock(MockOidcServer) authorization-code 전체 흐름(oidcLogin 우회 금지,
 *       Phase 3 lesson). 인프라 헬퍼는 {@link BffAuthIT} 형판을 계승한다.
 * </ul>
 */
@AutoConfigureMockMvc
class BffAuthSessionIT extends AbstractIntegrationTest {

    private static final MockOidcServer OIDC = new MockOidcServer();

    static {
        // issuer-uri eager discovery 전에 WireMock 이 떠 있어야 한다(BffAuthIT 동일 사유).
        OIDC.start();
    }

    @DynamicPropertySource
    static void oidcRegistration(DynamicPropertyRegistry registry) {
        String base = OIDC.baseUrl();
        String reg = "spring.security.oauth2.client.registration.test-idp.";
        registry.add(reg + "client-id", () -> "test-client-id");
        registry.add(reg + "client-secret", () -> "test-client-secret");
        registry.add(reg + "client-name", () -> "Test IdP");
        registry.add(reg + "authorization-grant-type", () -> "authorization_code");
        registry.add(reg + "redirect-uri", () -> "{baseUrl}/login/oauth2/code/{registrationId}");
        registry.add(reg + "scope", () -> "openid,email");

        String prov = "spring.security.oauth2.client.provider.test-idp.";
        registry.add(prov + "issuer-uri", () -> base);
        registry.add(prov + "authorization-uri", () -> base + "/authorize");
        registry.add(prov + "token-uri", () -> base + "/token");
        registry.add(prov + "jwk-set-uri", () -> base + "/jwks");
        registry.add(prov + "user-name-attribute", () -> "sub");
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    IdentityApplicationService identityService;

    @Autowired
    SessionRepository<? extends Session> sessionRepository;

    @LocalServerPort
    int port;

    private final RestTemplate rest = noRedirectRestTemplate();

    // ===================== SC#1: session 상태 분기 (AUTH-06) =====================

    @Test
    void sessionUnauthenticatedReturns200() throws Exception {
        // 비인증 접근이 401 이 아니라 200 {authenticated:false} — SPA 401 노이즈 제거.
        mvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.userId").doesNotExist());
    }

    @Test
    void sessionAuthenticatedReturnsUserId() throws Exception {
        // 인증 상태(user_id=42 claim) → 200 {authenticated:true, userId:42}.
        mvc.perform(get("/api/auth/session")
                        .with(oidcLogin().userInfoToken(u -> u.claim("user_id", 42L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.userId").value(42));
    }

    // ===================== SC#4: permitAll 매처 — 기존 보호 매처 불변 =====================

    @Test
    void permitAllMatchers() throws Exception {
        // /api/auth/session·/api/auth/login 은 비인증 접근 가능, /api/me 는 여전히 401.
        mvc.perform(get("/api/auth/session")).andExpect(status().isOk());
        mvc.perform(get("/api/auth/login")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    // ===================== SC#2: logout 204 + Redis 세션 삭제 (AUTH-07) =====================

    @Test
    void logoutReturns204AndInvalidatesSession() {
        String email = uniqueEmail("sc2");
        String oid = uniqueOid();
        identityService.invite(email);

        LoginResult login = performWireMockLogin(oid, email);
        String sessionCookie = login.sessionCookie();
        String sessionId = decodeSessionId(sessionCookie);

        assertThat(meStatus(sessionCookie)).isEqualTo(HttpStatus.OK);
        assertThat(sessionRepository.findById(sessionId)).isNotNull();

        // 302 redirect 아닌 204 No Content (BFF JSON 흐름).
        ResponseEntity<Void> logoutResp = logout(login);
        assertThat(logoutResp.getStatusCode().value()).isEqualTo(204);

        // 서버측 상태 read-back: Redis 세션 삭제 + 동일 세션 쿠키로 보호 요청 401.
        assertThat(sessionRepository.findById(sessionId))
                .as("로그아웃으로 Redis 세션이 삭제돼야 함").isNull();
        assertThat(meStatus(sessionCookie)).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===================== SC#3: login 302 + returnTo + open-redirect (AUTH-10) =====================

    @Test
    void loginRedirectsToOAuthEndpoint() {
        ResponseEntity<Void> resp = rest.exchange(
                baseUrl() + "/api/auth/login", HttpMethod.GET, HttpEntity.EMPTY, Void.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(302);
        URI loc = resp.getHeaders().getLocation();
        assertThat(loc).isNotNull();
        assertThat(loc.toString()).contains("/oauth2/authorization/");
    }

    @Test
    void loginWithReturnToRedirectsAfterLogin() {
        String email = uniqueEmail("sc3-rto");
        String oid = uniqueOid();
        identityService.invite(email);

        // 진입(/api/auth/login?returnTo=/dashboard) → RETURN_TO 세션 저장 → authorize → 콜백 →
        // oauth2Login successHandler 가 RETURN_TO 복원 → 최종 Location == /dashboard.
        ResponseEntity<Void> callback =
                performWireMockLoginViaLoginEndpoint(oid, email, "/api/auth/login?returnTo=/dashboard");

        URI loc = callback.getHeaders().getLocation();
        assertThat(loc).as("successHandler 가 returnTo 복귀 경로로 리다이렉트해야 함").isNotNull();
        assertThat(loc.toString()).endsWith("/dashboard");
    }

    @Test
    void loginRejectsAbsoluteReturnTo() {
        // 절대 URL·프로토콜-상대 URL·역슬래시 우회는 모두 무시되고 evil.com 으로 리다이렉트되지 않는다.
        // 역슬래시(/\evil.com)는 브라우저가 //evil.com 으로 정규화하는 open-redirect 우회라 반드시 거부돼야 한다.
        for (String hostile : List.of(
                "https://evil.com",
                "//evil.com",
                "/\\evil.com",
                "/%5Cevil.com")) {
            ResponseEntity<Void> resp = rest.exchange(
                    baseUrl() + "/api/auth/login?returnTo=" + hostile,
                    HttpMethod.GET, HttpEntity.EMPTY, Void.class);
            assertThat(resp.getHeaders().getLocation())
                    .as("returnTo=%s 는 302 를 내야 함", hostile).isNotNull();
            assertThat(resp.getHeaders().getLocation().toString())
                    .as("returnTo=%s 가 evil.com 으로 리다이렉트되면 안 됨", hostile)
                    .doesNotContain("evil.com");
        }
    }

    // ===================== WireMock authorization-code 흐름 헬퍼 (BffAuthIT 계승) =====================

    private LoginResult performWireMockLogin(String oid, String email) {
        ResponseEntity<Void> authorizeRedirect = rest.exchange(
                baseUrl() + "/oauth2/authorization/test-idp", HttpMethod.GET,
                HttpEntity.EMPTY, Void.class);
        URI location = authorizeRedirect.getHeaders().getLocation();
        String authRequestCookie = requireSessionCookie(authorizeRedirect.getHeaders());

        Map<String, String> q = queryParams(location);
        String state = q.get("state");
        String nonce = q.get("nonce");

        OIDC.stubToken(oid, email, nonce);

        String callbackUrl = baseUrl() + "/login/oauth2/code/test-idp?code=mock-code&state=" + state;
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, authRequestCookie);
        ResponseEntity<Void> callback = rest.exchange(
                callbackUrl, HttpMethod.GET, new HttpEntity<>(headers), Void.class);

        List<String> setCookies = callback.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        String sessionCookie = sessionCookieFrom(setCookies);
        if (sessionCookie == null) {
            sessionCookie = authRequestCookie;
        }
        return new LoginResult(sessionCookie, setCookies);
    }

    /**
     * {@link #performWireMockLogin} 의 변형 — 진입점을 {@code /api/auth/login?returnTo=...} 로 교체해
     * RETURN_TO 세션 저장부터 콜백까지 전체 흐름을 한 세션으로 수행한다. 콜백 응답(successHandler 의
     * 최종 리다이렉트)을 반환해 returnTo 복귀 경로를 단언할 수 있게 한다.
     */
    private ResponseEntity<Void> performWireMockLoginViaLoginEndpoint(String oid, String email, String entryUrl) {
        // 0) 진입: RETURN_TO 세션 저장 + 302 to /oauth2/authorization/test-idp. 세션 쿠키 캡처.
        ResponseEntity<Void> entry = rest.exchange(
                baseUrl() + entryUrl, HttpMethod.GET, HttpEntity.EMPTY, Void.class);
        String entrySession = requireSessionCookie(entry.getHeaders());

        // 1) authorize 리다이렉트 — 진입 세션 쿠키를 실어 RETURN_TO 보존.
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.add(HttpHeaders.COOKIE, entrySession);
        ResponseEntity<Void> authorizeRedirect = rest.exchange(
                baseUrl() + "/oauth2/authorization/test-idp", HttpMethod.GET,
                new HttpEntity<>(authHeaders), Void.class);
        URI location = authorizeRedirect.getHeaders().getLocation();
        String authRequestCookie = sessionCookieFrom(authorizeRedirect.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE));
        if (authRequestCookie == null) {
            authRequestCookie = entrySession;
        }

        Map<String, String> q = queryParams(location);
        String state = q.get("state");
        String nonce = q.get("nonce");
        OIDC.stubToken(oid, email, nonce);

        // 2) 콜백 → 인증 성공 → changeSessionId(attribute 보존) → successHandler 가 RETURN_TO 복원.
        String callbackUrl = baseUrl() + "/login/oauth2/code/test-idp?code=mock-code&state=" + state;
        HttpHeaders cbHeaders = new HttpHeaders();
        cbHeaders.add(HttpHeaders.COOKIE, authRequestCookie);
        return rest.exchange(callbackUrl, HttpMethod.GET, new HttpEntity<>(cbHeaders), Void.class);
    }

    private ResponseEntity<Void> logout(LoginResult login) {
        HttpHeaders getHeaders = new HttpHeaders();
        getHeaders.add(HttpHeaders.COOKIE, login.sessionCookie());
        ResponseEntity<Void> probe = rest.exchange(
                baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(getHeaders), Void.class);
        String csrfCookie = cookieValue(probe.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE),
                "XSRF-TOKEN");

        HttpHeaders headers = new HttpHeaders();
        StringBuilder cookie = new StringBuilder(login.sessionCookie());
        if (csrfCookie != null) {
            cookie.append("; XSRF-TOKEN=").append(csrfCookie);
            headers.add("X-XSRF-TOKEN", csrfCookie);
        }
        headers.add(HttpHeaders.COOKIE, cookie.toString());
        return rest.exchange(baseUrl() + "/api/auth/logout", HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);
    }

    private HttpStatus meStatus(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        ResponseEntity<String> resp = rest.exchange(
                baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return HttpStatus.valueOf(resp.getStatusCode().value());
    }

    // ===================== 저수준 유틸 (BffAuthIT 계승) =====================

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static RestTemplate noRedirectRestTemplate() {
        RestTemplate template =
                new RestTemplate(new org.springframework.http.client.SimpleClientHttpRequestFactory() {
                    @Override
                    protected void prepareConnection(java.net.HttpURLConnection connection,
                            String httpMethod) throws java.io.IOException {
                        super.prepareConnection(connection, httpMethod);
                        connection.setInstanceFollowRedirects(false);
                    }
                });
        template.setErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(org.springframework.http.client.ClientHttpResponse response) {
            }
        });
        return template;
    }

    private static String requireSessionCookie(HttpHeaders headers) {
        String c = sessionCookieFrom(headers.getOrEmpty(HttpHeaders.SET_COOKIE));
        if (c == null) {
            throw new IllegalStateException("SESSION 쿠키가 없음: " + headers);
        }
        return c;
    }

    private static String sessionCookieFrom(List<String> setCookies) {
        String value = cookieValue(setCookies, "SESSION");
        return value == null ? null : "SESSION=" + value;
    }

    private static String cookieValue(List<String> setCookies, String name) {
        for (String sc : setCookies) {
            String first = sc.split(";", 2)[0].trim();
            int eq = first.indexOf('=');
            if (eq > 0 && first.substring(0, eq).equals(name)) {
                String v = first.substring(eq + 1);
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return null;
    }

    private static String decodeSessionId(String sessionCookie) {
        String value = sessionCookie.substring("SESSION=".length());
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Map<String, String> queryParams(URI uri) {
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        java.util.HashMap<String, String> result = new java.util.HashMap<>();
        params.forEach((k, v) -> {
            if (!v.isEmpty()) {
                result.put(k, java.net.URLDecoder.decode(v.get(0), StandardCharsets.UTF_8));
            }
        });
        return result;
    }

    private static String uniqueEmail(String tag) {
        return tag + "-" + System.nanoTime() + "@example.com";
    }

    private static String uniqueOid() {
        return "oid-" + System.nanoTime();
    }

    private record LoginResult(String sessionCookie, List<String> setCookies) {
    }
}
