package com.anchors.baseline.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.auth.infrastructure.security.BaselineOidcUser;
import com.anchors.baseline.auth.support.MockOidcServer;
import com.anchors.baseline.identity.application.IdentityApplicationService;
import com.anchors.baseline.identity.domain.model.Email;
import com.anchors.baseline.identity.domain.model.User;
import com.anchors.baseline.identity.domain.model.UserStatus;
import com.anchors.baseline.identity.domain.repository.UserRepository;
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
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * BFF 인증 SC#1~4 · AUTH-01~05 행위 단언 통합 테스트(AbstractIntegrationTest 상속 — Testcontainers
 * Postgres + Redis). SC#1·SC#4·AUTH-01 은 WireMock(MockOidcServer)로 실제 authorization-code 토큰
 * 교환 경로를 태운다(충실도 하한 — oidcLogin 은 토큰 교환·실 Redis 세션을 우회하므로 SC#1 의
 * "토큰이 Redis 세션 attribute 에"·SC#4 의 "Redis 세션 삭제"를 증명하지 못함). SC#2·SC#3·멱등은 oidcLogin().
 */
@AutoConfigureMockMvc
class BffAuthIT extends AbstractIntegrationTest {

    private static final MockOidcServer OIDC = new MockOidcServer();
    private static final String AUTHORIZED_CLIENTS_ATTR =
            HttpSessionOAuth2AuthorizedClientRepository.class.getName() + ".AUTHORIZED_CLIENTS";

    static {
        // issuer-uri 는 컨텍스트 시작 시 eager OIDC discovery 를 유발하므로, 컨텍스트 로드(=@DynamicPropertySource
        // 평가)보다 먼저 WireMock 이 떠 있어야 한다. @BeforeAll 은 컨텍스트 로드 이후라 늦다 → static 초기화에서 기동.
        OIDC.start();
    }

    @DynamicPropertySource
    static void oidcRegistration(DynamicPropertyRegistry registry) {
        String base = OIDC.baseUrl();
        // registration 과 provider 를 모두 여기서 주입한다(공유 application-test.yml 에 두면 provider URI
        // 가 없는 다른 통합 테스트 컨텍스트가 깨지므로 BffAuthIT 컨텍스트에만 한정).
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
    UserRepository userRepository;

    @Autowired
    IdentityApplicationService identityService;

    @Autowired
    SessionRepository<? extends Session> sessionRepository;

    @LocalServerPort
    int port;

    private final RestTemplate rest = noRedirectRestTemplate();

    // ---- AUTH-01: WireMock authorization-code 흐름이 인증 성공으로 귀결 ----
    @Test
    void oidcLoginFlowSucceeds() {
        String email = uniqueEmail("auth01");
        String oid = uniqueOid();
        identityService.invite(email);

        LoginResult login = performWireMockLogin(oid, email);

        assertThat(login.sessionCookie()).isNotBlank();
        assertThat(meStatus(login.sessionCookie())).isEqualTo(HttpStatus.OK);
    }

    // ---- AUTH-02 / SC#1 (CORE, WireMock): 토큰은 Redis 세션 attribute 에만, 쿠키엔 SESSION 만 ----
    @Test
    void tokenOnlyInRedisNotInCookie() {
        String email = uniqueEmail("sc1");
        String oid = uniqueOid();
        identityService.invite(email);

        LoginResult login = performWireMockLogin(oid, email);

        // (a) 쿠키엔 SESSION ID 만 — access/refresh 토큰 문자열이 어떤 Set-Cookie 에도 없음.
        assertThat(login.sessionCookie()).isNotBlank();
        for (String setCookie : login.setCookies()) {
            assertThat(setCookie)
                    .doesNotContain("mock-access-token")
                    .doesNotContain("mock-refresh-token");
        }

        // (b) 캡처한 SESSION id 로 Redis 세션을 직접 로드 → HttpSession attribute(AUTHORIZED_CLIENTS)에
        //     OAuth2AuthorizedClient 가 있고 access token 이 비어있지 않음(OAuth2AuthorizedClientService 미사용).
        Session session = sessionRepository.findById(decodeSessionId(login.sessionCookie()));
        assertThat(session).as("Redis 세션이 SESSION id 로 로드돼야 함").isNotNull();

        Object attr = session.getAttribute(AUTHORIZED_CLIENTS_ATTR);
        assertThat(attr).as("HttpSession 의 AUTHORIZED_CLIENTS attribute").isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, OAuth2AuthorizedClient> clients = (Map<String, OAuth2AuthorizedClient>) attr;
        assertThat(clients).isNotEmpty();
        OAuth2AuthorizedClient client = clients.values().iterator().next();
        assertThat(client.getAccessToken().getTokenValue())
                .as("Redis 세션에 보관된 access token")
                .isNotBlank()
                .startsWith("mock-access-token");
    }

    // ---- AUTH-03 / SC#2 (oidcLogin): 쿠키 세션만으로 /api/me 200, userId == 로컬 User.id ----
    @Test
    void cookieSessionAuthenticatesApi() throws Exception {
        String email = uniqueEmail("sc2");
        String oid = uniqueOid();
        Long userId = identityService.invite(email);

        mvc.perform(get("/api/me").with(oidcLogin()
                        .idToken(t -> t.subject(oid).claim("email", email))
                        .userInfoToken(u -> u.claim("user_id", userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.intValue()));
    }

    // ---- AUTH-04 / SC#4 (WireMock 세션 라운드트립): 로그아웃 후 401 + Redis 세션 삭제 ----
    @Test
    void logoutInvalidatesSession() {
        String email = uniqueEmail("sc4");
        String oid = uniqueOid();
        identityService.invite(email);

        LoginResult login = performWireMockLogin(oid, email);
        String sessionCookie = login.sessionCookie();
        String sessionId = decodeSessionId(sessionCookie);

        // 실제 세션 확인: /api/me 200 + Redis 세션 존재
        assertThat(meStatus(sessionCookie)).isEqualTo(HttpStatus.OK);
        assertThat(sessionRepository.findById(sessionId)).isNotNull();

        // 로그아웃 (CSRF 토큰은 CookieCsrfTokenRepository.withHttpOnlyFalse → XSRF-TOKEN 쿠키에서 회수)
        logout(login);

        // 로그아웃 후 동일 SESSION 쿠키 재요청은 401(인증 실패) + Redis 세션 삭제됨.
        assertThat(meStatus(sessionCookie)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(sessionRepository.findById(sessionId))
                .as("로그아웃으로 Redis 세션이 삭제돼야 함").isNull();
    }

    // ---- AUTH-05 / SC#3: 최초 로그인 시 linkIdentity → INVITED→ACTIVE, externalId=oid ----
    @Test
    void firstLoginLinksIdentity() {
        String email = uniqueEmail("sc3");
        String oid = uniqueOid();
        Long userId = identityService.invite(email);
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.INVITED);

        performWireMockLogin(oid, email);

        User linked = userRepository.findById(userId).orElseThrow();
        assertThat(linked.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(linked.getExternalId()).isEqualTo(oid);
    }

    // ---- AUTH-05 멱등: 이미 ACTIVE(externalId 연결) 사용자 재로그인 → 재연결 없음, ACTIVE 유지 ----
    @Test
    void reLoginIsIdempotent() {
        String email = uniqueEmail("sc3idem");
        String oid = uniqueOid();
        Long userId = identityService.invite(email);
        identityService.linkIdentity(userId, oid, "기존 사용자");
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);

        // findByExternalId 매칭 분기 — linkIdentity 미호출(INVITED 가드를 건드리지 않음), 예외 없음.
        LoginResult login = performWireMockLogin(oid, email);
        assertThat(meStatus(login.sessionCookie())).isEqualTo(HttpStatus.OK);

        User after = userRepository.findById(userId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(after.getExternalId()).isEqualTo(oid);
    }

    // ---- D-04: 미초대 이메일 로그인 거부 ----
    @Test
    void uninvitedLoginIsRejected() {
        String email = uniqueEmail("uninvited");
        String oid = uniqueOid();
        // invite 하지 않음 → findByExternalId/findByEmail 모두 부재 → 인증 거부.

        LoginResult login = performWireMockLogin(oid, email);

        // 콜백이 인증 실패로 귀결(세션에 인증 principal 없음) → /api/me 401.
        assertThat(meStatus(login.sessionCookie())).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(userRepository.findByEmail(new Email(email))).isEmpty();
    }

    // ===================== WireMock authorization-code 흐름 헬퍼 =====================

    /**
     * 실제 authorization-code 흐름을 태운다:
     * 1) GET /oauth2/authorization/test-idp → 302 to WireMock authorize (state/nonce 발급, 인증요청 세션 쿠키)
     * 2) authorize URL 에서 state·nonce 추출 → MockOidcServer /token 스텁(해당 nonce echo)
     * 3) 동일 쿠키로 GET /login/oauth2/code/test-idp?code=...&state=... → 서버 토큰 교환 + id_token 검증 + 세션 생성
     * 반환: 콜백 응답의 Set-Cookie 들과 캡처한 SESSION 쿠키.
     */
    private LoginResult performWireMockLogin(String oid, String email) {
        // 1) authorize 리다이렉트 트리거
        ResponseEntity<Void> authorizeRedirect = rest.exchange(
                baseUrl() + "/oauth2/authorization/test-idp", HttpMethod.GET,
                HttpEntity.EMPTY, Void.class);
        URI location = authorizeRedirect.getHeaders().getLocation();
        String authRequestCookie = requireSessionCookie(authorizeRedirect.getHeaders());

        Map<String, String> q = queryParams(location);
        String state = q.get("state");
        String nonce = q.get("nonce");

        // 2) /token 스텁에 이 흐름의 nonce 를 echo 하도록 등록(OIDC nonce 검증 충실).
        OIDC.stubToken(oid, email, nonce);

        // 3) 콜백 — authorize 단계 세션 쿠키를 실어 state/nonce 연속성 보장
        String callbackUrl = baseUrl() + "/login/oauth2/code/test-idp?code=mock-code&state="
                + state;
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, authRequestCookie);
        ResponseEntity<Void> callback = rest.exchange(
                callbackUrl, HttpMethod.GET, new HttpEntity<>(headers), Void.class);

        List<String> setCookies = callback.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);
        String sessionCookie = sessionCookieFrom(setCookies);
        // 콜백이 인증 후 새 세션 쿠키를 안 내릴 수도 있음(미초대 거부 등) → authorize 쿠키로 폴백.
        if (sessionCookie == null) {
            sessionCookie = authRequestCookie;
        }
        return new LoginResult(sessionCookie, setCookies);
    }

    private void logout(LoginResult login) {
        // CSRF 토큰 확보: GET /api/me(혹은 임의 GET)로 XSRF-TOKEN 쿠키를 받는다.
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
        rest.exchange(baseUrl() + "/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
    }

    private HttpStatus meStatus(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        ResponseEntity<String> resp = rest.exchange(
                baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return HttpStatus.valueOf(resp.getStatusCode().value());
    }

    // ===================== 저수준 유틸 =====================

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
        // 4xx/5xx 를 예외로 던지지 않음 — 401/302 를 단언 대상 상태로 직접 검사.
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
            throw new IllegalStateException("authorize 단계 SESSION 쿠키가 없음: " + headers);
        }
        return c;
    }

    /** Set-Cookie 목록에서 SESSION 쿠키를 "SESSION=value" 형태로 반환(없으면 null). */
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

    /** SESSION 쿠키 값(Base64 인코딩된 세션 id)을 디코드해 SessionRepository.findById 키로 변환. */
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
