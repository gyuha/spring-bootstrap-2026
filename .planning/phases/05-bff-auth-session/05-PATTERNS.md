# Phase 5: BFF 인증 세션 엔드포인트 — 패턴 맵

**작성일:** 2026-05-31
**분석 대상 파일:** 4개 (신규 2 / 수정 2)
**Analog 확보:** 4 / 4

---

## 파일 분류

| 신규/수정 파일 | 역할 | 데이터 흐름 | 가장 가까운 Analog | 매치 품질 |
|---|---|---|---|---|
| `auth/interfaces/AuthController.java` | controller | request-response | `auth/interfaces/MeController.java` | 정확 (동일 패키지, 동일 `OidcUser` 읽기 패턴) |
| `auth/infrastructure/security/SecurityConfig.java` | config / security | request-response | 자기 자신 (수정 대상) | — (수정, analog 불필요) |
| `auth/BffAuthSessionIT.java` | test (IT) | request-response | `auth/BffAuthIT.java` | 정확 (동일 Testcontainers 상속, WireMock 흐름 동일) |
| `auth/BffAuthIT.java` (수정) | test (IT) | request-response | 자기 자신 (수정 대상) | — (logout URL 경로만 수정) |

---

## 패턴 배정

### 1. `auth/interfaces/AuthController.java` (controller, request-response)

**Analog:** `src/main/java/com/anchors/baseline/auth/interfaces/MeController.java`

**임포트 패턴** (MeController.java, 1~8행):
```java
package com.anchors.baseline.auth.interfaces;

import java.util.Collections;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
```

AuthController는 여기에 추가로 아래를 임포트한다:
```java
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.fasterxml.jackson.annotation.JsonInclude;
```

**ArchUnit 제약 (D-06):** `auth/interfaces`는 `Application`, `Domain` 계층만 의존 가능. `BaselineOidcUser`(infrastructure 타입) import/cast 금지 — `OidcUser.getAttribute("user_id")` 표준 읽기만 사용. `ClientRegistrationRepository`는 외부 라이브러리 타입이라 `..infrastructure..` 패턴에 매칭되지 않으므로 ArchUnit 통과.

**session 엔드포인트 핵심 패턴** (MeController.java, 19~24행 변형):
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // GET /api/auth/session (AUTH-06, SC#1)
    // MeController.me()와 동일 getAttribute 패턴, null 분기 추가
    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) {
            return new SessionResponse(false, null);
        }
        // MeController과 동일한 표준 읽기 경로(infrastructure 타입 미참조)
        return new SessionResponse(true, principal.getAttribute("user_id"));
    }

    // record: userId null 허용. 비인증 시 JSON에서 userId 키 제외(@JsonInclude)
    record SessionResponse(
            boolean authenticated,
            @JsonInclude(JsonInclude.Include.NON_NULL) Object userId) {}
}
```

**null 안전성 패턴** (MeController.java, 21~23행 주석):
```java
// singletonMap은 null 값을 허용한다(Map.of는 NPE). 표준 OidcUser가 principal일 경우
// {"userId":null}로 안전하게 응답한다.
// → Phase 5에서는 record DTO의 @JsonInclude(NON_NULL)로 동일 목적 달성
return Collections.singletonMap("userId", principal.getAttribute("user_id"));
```

**login 래퍼 핵심 패턴** (RESEARCH.md Pattern 3 + SecurityConfig 35~88행의 ObjectProvider 패턴):
```java
// GET /api/auth/login (AUTH-10, SC#3)
@GetMapping("/login")
public void login(
        @RequestParam(required = false) String returnTo,
        HttpSession session,
        HttpServletResponse response) throws IOException {
    // 1. open-redirect 방지: 상대경로만 허용(절대 URL, 프로토콜 상대 URL 거부)
    if (isSafeRelativePath(returnTo)) {
        session.setAttribute("RETURN_TO", returnTo);
    }
    // 2. registration 부재 가드(main 프로파일 — ObjectProvider 패턴 계승)
    if (clientRegistrationRepository.getIfAvailable() == null) {
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "OIDC registration not configured");
        return;
    }
    // 3. OIDC authorization 진입점으로 302
    response.sendRedirect("/oauth2/authorization/" + registrationId);
}

// open-redirect 방지 검증(classpath 실측: UrlUtils.isValidRedirectUrl은 절대URL도 허용)
private static boolean isSafeRelativePath(String url) {
    return url != null
            && url.startsWith("/")
            && !url.startsWith("//")
            && !url.contains("://");
}
```

---

### 2. `auth/infrastructure/security/SecurityConfig.java` (수정 대상)

**Analog:** 자기 자신. 3곳의 외과적 수정만 적용한다. 나머지 코드는 무변경.

**수정 1 — logout DSL** (SecurityConfig.java, 56~60행 현재값 → 교체 목표):
```java
// 현재 (Phase 3)
.logout(logout -> logout
        .logoutUrl("/logout")           // ← 재지정 대상
        .invalidateHttpSession(true)
        .clearAuthentication(true)
        .deleteCookies("SESSION"))      // ← logoutSuccessHandler 추가 대상

// Phase 5 목표
.logout(logout -> logout
        .logoutUrl("/api/auth/logout")  // /logout → /api/auth/logout 재지정
        .invalidateHttpSession(true)
        .clearAuthentication(true)
        .deleteCookies("SESSION")
        .logoutSuccessHandler(          // 302 → 204로 교체
                new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
```

주의: `logoutSuccessHandler()` 설정은 `logoutSuccessUrl()` 설정을 무시하지만, `invalidateHttpSession` / `clearAuthentication` / `deleteCookies`(LogoutHandler 체인)에는 영향이 없다 — classpath 실측 확인.

**수정 2 — permitAll 매처** (SecurityConfig.java, 52~55행 현재값 → 교체 목표):
```java
// 현재 (Phase 3)
.requestMatchers("/actuator/health", "/login/**", "/oauth2/**",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()

// Phase 5 목표 — 2개 경로 추가, 구체 매처 선행
.requestMatchers("/actuator/health", "/login/**", "/oauth2/**",
        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
        "/api/auth/session", "/api/auth/login").permitAll()  // ← 신규 2개 추가
```

`/api/auth/logout`은 추가하지 않는다(D-05). `/api/**` 401 엔트리포인트(65~69행)는 무변경.

**수정 3 — oauth2Login successHandler 추가** (SecurityConfig.java, 76~78행 현재값 → 교체 목표):
```java
// 현재 (Phase 3)
http.oauth2Login(oauth -> oauth
        .userInfoEndpoint(ui -> ui.oidcUserService(baselineOidcUserService)));

// Phase 5 목표 — returnTo 복원 successHandler 연결
http.oauth2Login(oauth -> oauth
        .userInfoEndpoint(ui -> ui.oidcUserService(baselineOidcUserService))
        .successHandler((request, response, authentication) -> {
            HttpSession session = request.getSession(false);
            String returnTo = (session != null)
                    ? (String) session.getAttribute("RETURN_TO") : null;
            if (session != null) {
                session.removeAttribute("RETURN_TO");
            }
            response.sendRedirect(returnTo != null ? returnTo : "/");
        }));
```

**신규 임포트 추가** (SecurityConfig.java 상단):
```java
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import jakarta.servlet.http.HttpSession;
```

---

### 3. `auth/BffAuthSessionIT.java` (신규 IT, test)

**Analog:** `src/test/java/com/anchors/baseline/auth/BffAuthIT.java`

**클래스 선언 + 공유 인프라** (BffAuthIT.java, 49~99행 패턴 계승):
```java
@AutoConfigureMockMvc
class BffAuthSessionIT extends AbstractIntegrationTest {

    private static final MockOidcServer OIDC = new MockOidcServer();

    static {
        OIDC.start();  // 컨텍스트 로드 전 WireMock 기동 필수(issuer-uri eager discovery)
    }

    @DynamicPropertySource
    static void oidcRegistration(DynamicPropertyRegistry registry) {
        // BffAuthIT과 동일한 registration 주입 패턴
        String base = OIDC.baseUrl();
        String reg = "spring.security.oauth2.client.registration.test-idp.";
        // ... (BffAuthIT.java 63~81행 그대로 복사)
    }

    @Autowired MockMvc mvc;
    @Autowired SessionRepository<? extends Session> sessionRepository;
    @LocalServerPort int port;
    private final RestTemplate rest = noRedirectRestTemplate();
}
```

**SC#1 단언 패턴** (BffAuthIT.java의 oidcLogin() post-processor 패턴 + MockMvc 비인증 패턴):
```java
// 비인증 — 401이 아니라 200 {authenticated:false}
@Test
void sessionUnauthenticated() throws Exception {
    mvc.perform(get("/api/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(false))
            .andExpect(jsonPath("$.userId").doesNotExist());
}

// 인증 — oidcLogin() post-processor (상태 단언만 필요, WireMock 불필요)
@Test
void sessionAuthenticated() throws Exception {
    Long userId = 42L;
    mvc.perform(get("/api/auth/session").with(oidcLogin()
                    .userInfoToken(u -> u.claim("user_id", userId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.userId").value(userId.intValue()));
}
```

**SC#2 단언 패턴** (BffAuthIT.java 164~184행 `logoutInvalidatesSession` 패턴 + 경로 갱신):
```java
// WireMock 실 토큰 교환 필수 (서버측 Redis 세션 상태 read-back)
@Test
void logoutReturns204AndInvalidatesSession() {
    // BffAuthIT.performWireMockLogin() 패턴 재사용
    LoginResult login = performWireMockLogin(oid, email);
    String sessionCookie = login.sessionCookie();
    String sessionId = decodeSessionId(sessionCookie);

    // 로그아웃 (CSRF 패턴: BffAuthIT.logout() 헬퍼 — 경로는 /api/auth/logout)
    ResponseEntity<Void> logoutResp = logout(login);
    assertThat(logoutResp.getStatusCode().value()).isEqualTo(204);          // 302 아님

    // Redis 세션 삭제 + 보호 API 401
    assertThat(sessionRepository.findById(sessionId)).isNull();
    assertThat(meStatus(sessionCookie)).isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

**SC#2 logout 헬퍼** (BffAuthIT.java 276~293행 패턴 — 경로 `/logout` → `/api/auth/logout`):
```java
// BffAuthIT.logout() 패턴 그대로 복사, URL만 변경
private ResponseEntity<Void> logout(LoginResult login) {
    HttpHeaders getHeaders = new HttpHeaders();
    getHeaders.add(HttpHeaders.COOKIE, login.sessionCookie());
    ResponseEntity<Void> probe = rest.exchange(
            baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(getHeaders), Void.class);
    String csrfCookie = cookieValue(probe.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");

    HttpHeaders headers = new HttpHeaders();
    StringBuilder cookie = new StringBuilder(login.sessionCookie());
    if (csrfCookie != null) {
        cookie.append("; XSRF-TOKEN=").append(csrfCookie);
        headers.add("X-XSRF-TOKEN", csrfCookie);
    }
    headers.add(HttpHeaders.COOKIE, cookie.toString());
    return rest.exchange(baseUrl() + "/api/auth/logout",   // ← /logout에서 변경
            HttpMethod.POST, new HttpEntity<>(headers), Void.class);
}
```

**SC#3 단언 패턴** (noRedirectRestTemplate 패턴 재사용):
```java
// 302 리다이렉트 단언 (noRedirectRestTemplate — BffAuthIT.java 309~331행 패턴)
@Test
void loginRedirectsToOAuthEndpoint() {
    ResponseEntity<Void> resp = rest.exchange(
            baseUrl() + "/api/auth/login", HttpMethod.GET, HttpEntity.EMPTY, Void.class);
    assertThat(resp.getStatusCode().value()).isEqualTo(302);
    assertThat(resp.getHeaders().getLocation().toString())
            .contains("/oauth2/authorization/");
}

// open-redirect 거부 단언
@Test
void loginRejectsAbsoluteReturnTo() {
    ResponseEntity<Void> resp = rest.exchange(
            baseUrl() + "/api/auth/login?returnTo=https://evil.com",
            HttpMethod.GET, HttpEntity.EMPTY, Void.class);
    // Location이 /oauth2/authorization/으로만 향해야 함(evil.com 없음)
    String location = resp.getHeaders().getLocation().toString();
    assertThat(location).doesNotContain("evil.com");
}
```

**SC#4 단언 패턴** (비인증 접근 가능 + 기존 보호 API 불변):
```java
@Test
void permitAllMatchers() throws Exception {
    // /api/auth/session: 비인증 200 (401 아님)
    mvc.perform(get("/api/auth/session")).andExpect(status().isOk());
    // /api/auth/login: 비인증 302 (401 아님)
    mvc.perform(get("/api/auth/login")).andExpect(status().is3xxRedirection());
    // /api/me: 비인증 여전히 401 (매처 순서 회귀 방지)
    mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
}
```

**저수준 유틸** (BffAuthIT.java 303~388행 — 그대로 복사):
- `baseUrl()`, `noRedirectRestTemplate()`, `requireSessionCookie()`, `sessionCookieFrom()`, `cookieValue()`, `decodeSessionId()`, `queryParams()`, `uniqueEmail()`, `uniqueOid()`, `LoginResult` record — BffAuthIT에서 동일 구현 복사.

---

### 4. `auth/BffAuthIT.java` (수정 대상 — logout 헬퍼 경로)

**수정 위치:** BffAuthIT.java 292행

```java
// 현재 (Phase 3)
rest.exchange(baseUrl() + "/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);

// Phase 5 목표 — logoutUrl 재지정과 동기화
rest.exchange(baseUrl() + "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
```

이 1행 수정이 `logoutInvalidatesSession()` 테스트의 회귀를 방지한다. 이외 BffAuthIT은 무변경.

---

## 공유 패턴

### OidcUser 표준 읽기 (interfaces 계층 공통)

**출처:** `src/main/java/com/anchors/baseline/auth/interfaces/MeController.java`, 23행

```java
// infrastructure 타입(BaselineOidcUser) import/cast 없이 표준 attribute 읽기
// ArchUnit Interfaces → {Application, Domain} 규칙을 구조적으로 통과
principal.getAttribute("user_id")
```

**적용 대상:** `AuthController.session()`, `AuthController.login()` (userId 읽기 필요 시)

### ObjectProvider 가드 (infrastructure 미설정 방어)

**출처:** `src/main/java/com/anchors/baseline/auth/infrastructure/security/SecurityConfig.java`, 76~85행

```java
// main 프로파일에 ClientRegistrationRepository 빈이 없을 때 NPE/컨텍스트 실패 방지
if (clientRegistrationRepository.getIfAvailable() != null) {
    http.oauth2Login(/* ... */);
} else {
    log.warn("OIDC 로그인 비활성화 — ClientRegistrationRepository 빈이 없습니다. ...");
}
```

**적용 대상:** `AuthController.login()` — 동일 패턴으로 `ObjectProvider<ClientRegistrationRepository>` 주입, `getIfAvailable() == null` 시 503 반환.

### WireMock OIDC 흐름 헬퍼 (IT 공통)

**출처:** `src/test/java/com/anchors/baseline/auth/BffAuthIT.java`, 244~293행

`performWireMockLogin()`, `logout()`, `meStatus()`, `noRedirectRestTemplate()`, `decodeSessionId()` 5개 헬퍼를 `BffAuthSessionIT`에 그대로 복사하거나, `BffAuthIT`을 확장해 상속한다.

**적용 대상:** `BffAuthSessionIT` 전체

### CSRF 회신 패턴 (POST 상태 변경 공통)

**출처:** `src/test/java/com/anchors/baseline/auth/BffAuthIT.java`, 277~292행

```java
// GET 먼저 → XSRF-TOKEN 쿠키 획득 → POST에 X-XSRF-TOKEN 헤더 포함
HttpHeaders getHeaders = new HttpHeaders();
getHeaders.add(HttpHeaders.COOKIE, login.sessionCookie());
ResponseEntity<Void> probe = rest.exchange(
        baseUrl() + "/api/me", HttpMethod.GET, new HttpEntity<>(getHeaders), Void.class);
String csrfCookie = cookieValue(probe.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE), "XSRF-TOKEN");
// ... headers에 X-XSRF-TOKEN 추가 후 POST
```

**적용 대상:** `BffAuthSessionIT.logout()` 헬퍼

---

## Analog 없음

이 Phase에서 Analog가 없는 파일은 없다. 모든 신규/수정 파일이 기존 코드에서 직접 계승하는 패턴을 갖는다.

---

## 메타데이터

**Analog 탐색 범위:** `src/main/java/com/anchors/baseline/auth/`, `src/test/java/com/anchors/baseline/auth/`, `src/test/java/com/anchors/baseline/`

**스캔 파일:** 8개 (`MeController.java`, `SecurityConfig.java`, `BaselineOidcUser.java`, `BaselineOidcUserService.java`, `BffAuthIT.java`, `MockOidcServer.java`, `AbstractIntegrationTest.java`, `ArchitectureTest.java`)

**패턴 추출일:** 2026-05-31
