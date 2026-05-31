# Phase 5: BFF 인증 세션 엔드포인트 — Research

**작성일:** 2026-05-31
**도메인:** Spring Security 6.5.1 BFF 인증 — logout DSL / permitAll 매처 / OIDC login 래퍼
**신뢰도 종합:** HIGH (핵심 classpath 실측 완료)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** 신규 `auth/interfaces/AuthController` 신설. `MeController`(`/api/me`) 무변경.
- **D-02:** `SecurityConfig`의 `logout()` DSL 재사용. `logoutUrl("/logout")` → `/api/auth/logout`. `HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)` 추가. 세션 무효화·쿠키 삭제·인증 클리어는 기존 DSL 유지. 컨트롤러 직접 구현 금지.
- **D-03:** `GET /api/auth/session`은 permitAll. 비인증 → `200 {authenticated:false}`, 인증 → `200 {authenticated:true, userId:<로컬 User.id>}`. `@AuthenticationPrincipal OidcUser` nullable 수신. `Map.of` 금지.
- **D-04:** `GET /api/auth/login`은 `/oauth2/authorization/{registration}`으로 302 리다이렉트하는 얇은 래퍼. `returnTo` 보존. open-redirect 방지(상대경로만 허용). registration 부재 가드(`ObjectProvider`) 정합.
- **D-05:** `SecurityConfig.authorizeHttpRequests` permitAll 목록에 `/api/auth/session`·`/api/auth/login` 추가. `/api/auth/logout`은 추가 안 함. `/api/**` 401 엔트리포인트 불변 유지. 구체 매처 선행.
- **D-06:** Phase 5에서 ArchUnit 무변경. 신규 코드는 기존 규칙 자연 통과.
- **D-07:** 전 검증은 Testcontainers + WireMock + MockMvc IT. 라이브 구동 없음. `AbstractIntegrationTest` 상속. `BffAuthIT` 패턴 재사용·확장.

### Claude's Discretion

- 응답 DTO 형태(record vs LinkedHashMap)
- `AuthController` 메서드 시그니처
- `returnTo` 검증 구현(상대경로 정규식 vs `/`로 시작 단순 검사)
- 로그인 래퍼의 registration 파라미터 방식(고정 vs 쿼리 파라미터)
- `LogoutSuccessHandler` 구현(`HttpStatusReturningLogoutSuccessHandler` vs 커스텀)
- permitAll 매처 표현식
- 신규 IT 클래스 분리 vs `BffAuthIT` 확장

### Deferred Ideas (OUT OF SCOPE)

- `GET /api/auth/me` 신원·권한 집계 (AUTH-08/09) — Phase 6
- ArchUnit 계층 의존 규칙 정비 (AUTH-11) — Phase 6
- `/api/me`(MeController) 정리 — Phase 6
- 다중 registration 선택 UI / IdP 디스커버리
- 리프레시 토큰 자동 갱신, JIT 프로비저닝, 서버 간 호출, CORS

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | 설명 | Research 지원 사항 |
|----|------|-------------------|
| AUTH-06 | `GET /api/auth/session` — 비인증 시 `200 {authenticated:false}`, 인증 시 `200 {authenticated:true, userId}` | `@AuthenticationPrincipal(required=false)` nullable 주입 동작 실측, record DTO null-safe 패턴, permitAll 순서 |
| AUTH-07 | `POST /api/auth/logout` — 302 대신 204, HttpSession 무효화 + SESSION 쿠키 삭제 + 인증 클리어 | `HttpStatusReturningLogoutSuccessHandler` 생성자·동작 classpath 실측, `LogoutConfigurer.logoutSuccessHandler()` DSL 확인 |
| AUTH-10 | `GET /api/auth/login` — OIDC authorization 진입점 302 래퍼 + `returnTo` 처리 | `SavedRequestAwareAuthenticationSuccessHandler.setTargetUrlParameter()` API 확인, open-redirect 방지 설계, `ObjectProvider` 가드 정합 |

</phase_requirements>

---

## 요약

Phase 5는 신규 도메인 없이 **3개 HTTP 엔드포인트 + SecurityConfig 조정**만으로 완결된다.

기존 `SecurityConfig`(Spring Security 6.5.1 BFF 설정)는 이미 `logout()` DSL, CSRF, 401 엔트리포인트를 올바르게 선언하고 있다. Phase 5는 이를 **외과적으로 조정**한다:
1. `logoutUrl` 재지정 + `LogoutSuccessHandler` 교체(204)
2. `authorizeHttpRequests`에 2개 경로 추가
3. `oauth2Login.successHandler`에 `returnTo` 파라미터 인식 핸들러 연결

`AuthController`는 `auth/interfaces`에 배치되어 ArchUnit `Interfaces → {Application, Domain}` 규칙을 자연 통과한다(`OidcUser` 표준 읽기만 사용, infrastructure 타입 미참조).

**핵심 확정 사항 (classpath 실측):**
- `HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)` — 6.5.1 jar에 존재 [VERIFIED: classpath]
- `LogoutConfigurer.logoutSuccessHandler(LogoutSuccessHandler)` DSL — 6.5.1 sources에서 확인 [VERIFIED: classpath]
- `@AuthenticationPrincipal` `errorOnInvalidType = false` (기본) — 비인증 시 null 주입, 예외 없음 [VERIFIED: classpath]
- `SavedRequestAwareAuthenticationSuccessHandler.setTargetUrlParameter(String)` — AbstractAuthenticationTargetUrlRequestHandler에 존재 [VERIFIED: classpath]
- Spring Security `UrlUtils.isValidRedirectUrl()`은 절대 URL도 허용 → **open-redirect 방지는 직접 구현 필수** [VERIFIED: classpath]
- `.csrf().spa()`는 Spring Security 6.5.1에 부재(Phase 3 lesson 재확인) [VERIFIED: classpath — CsrfCookieFilter 직접 구현이 이미 존재]

**1차 권장 지침:** SecurityConfig 수정 3곳(logoutUrl, logoutSuccessHandler, permitAll 추가) + AuthController 신설 + returnTo 상대경로 검증 로직.

---

## Architectural Responsibility Map

| 기능 | 주 계층 | 보조 계층 | 근거 |
|------|---------|----------|------|
| GET /api/auth/session 응답 | Interfaces (AuthController) | — | 컨트롤러가 Principal null 분기 후 JSON 반환. 도메인 모델 불필요(§5.1) |
| POST /api/auth/logout 처리 | Infrastructure (SecurityConfig) | — | 프레임워크 logout DSL이 세션 무효화·쿠키 삭제·인증 클리어 수행. 컨트롤러 개입 없음 |
| GET /api/auth/login 리다이렉트 | Interfaces (AuthController) | Infrastructure (SecurityConfig) | 래퍼 컨트롤러가 returnTo 검증 후 /oauth2/authorization/{reg} 302. OIDC 흐름은 SecurityConfig oauth2Login DSL |
| returnTo open-redirect 방지 | Interfaces (AuthController) | — | returnTo 파라미터 검증(상대경로 여부)은 컨트롤러 책임. Spring Security UrlUtils는 절대 URL 허용하므로 자동 보호 없음 |
| permitAll 매처 순서 | Infrastructure (SecurityConfig) | — | authorizeHttpRequests 규칙 순서가 구체→광역 이어야 함. SecurityConfig 내부 수정 |
| CSRF 보호 | Infrastructure (SecurityConfig) | — | 기존 CookieCsrfTokenRepository + CsrfCookieFilter 유지. POST 로그아웃은 X-XSRF-TOKEN 필요 |

---

## Standard Stack

### Core

| 라이브러리 | 버전 | 목적 | 비고 |
|-----------|------|------|------|
| spring-security-web | 6.5.1 | `HttpStatusReturningLogoutSuccessHandler`, `LogoutSuccessHandler`, `PathPatternRequestMatcher` | Spring Boot 3.5.3 BOM 관리 |
| spring-security-config | 6.5.1 | `LogoutConfigurer.logoutSuccessHandler()`, `OAuth2LoginConfigurer.successHandler()` | Spring Boot 3.5.3 BOM 관리 |
| spring-security-oauth2-client | 6.5.1 | OIDC 인증 흐름, `SavedRequestAwareAuthenticationSuccessHandler` | 기존 의존성 재사용 |
| spring-session-data-redis | 3.5.1 | Redis 세션 — `SessionRepository.findById()` 로그아웃 후 null 단언 | 기존 의존성 재사용 |

**신규 의존성 없음(NFR-01).** Phase 3에서 추가된 `spring-boot-starter-oauth2-client` + `spring-session-data-redis`가 전부 커버.

### Supporting

| 라이브러리 | 버전 | 목적 | 사용 시점 |
|-----------|------|------|---------|
| spring-security-test | 6.5.1 | `oidcLogin()`, `csrf()` post-processor — IT에서 재사용 | 기존 테스트 의존성 |
| WireMock standalone | 3.13.1 | MockOidcServer — 실 토큰 교환 흐름 | SC#2(logout 세션 라운드트립) |

---

## Package Legitimacy Audit

신규 외부 패키지 없음(NFR-01 준수). slopcheck 실행 불필요.

---

## Architecture Patterns

### System Architecture Diagram

```
SPA (browser)
    │ GET /api/auth/session (permitAll)
    │ POST /api/auth/logout (CSRF token 필요)
    │ GET /api/auth/login?returnTo=/somewhere
    ▼
[AuthController] auth/interfaces
    ├── session() : @AuthenticationPrincipal OidcUser principal (nullable)
    │       null → {authenticated:false}
    │       non-null → {authenticated:true, userId: principal.getAttribute("user_id")}
    │
    ├── login(returnTo) : returnTo 상대경로 검증 → redirect /oauth2/authorization/{registration}
    │       (invalid/절대URL → redirect /oauth2/authorization/{registration}, returnTo 무시)
    │       (registration 부재 → 503 or 302 to /login — ObjectProvider 가드와 정합)
    │
    └── logout : SecurityConfig logout DSL이 처리 (컨트롤러 개입 없음)
            ↓
[SecurityConfig] auth/infrastructure/security
    ├── authorizeHttpRequests:
    │     /api/auth/session → permitAll (구체 먼저)
    │     /api/auth/login   → permitAll (구체 먼저)
    │     /api/**           → 401 엔트리포인트 (광역, 기존 불변)
    │     anyRequest        → authenticated
    │
    ├── logout DSL: /api/auth/logout POST → CSRF → invalidateSession + clearAuth + deleteCookies(SESSION) → 204
    │
    └── oauth2Login DSL: successHandler → SavedRequestAwareAuthenticationSuccessHandler
                          setTargetUrlParameter("returnTo")
                          setDefaultTargetUrl("/")
                                ↓
                         [Spring Security OIDC 흐름]
                         /oauth2/authorization/{reg} → IdP → callback → 세션 생성
                                ↓
                         [Redis 세션 저장] (기존 HttpSessionOAuth2AuthorizedClientRepository)
```

### 권장 파일 구조

```
src/main/java/com/anchors/baseline/auth/
├── interfaces/
│   ├── AuthController.java          ← 신규 (AUTH-06/10)
│   └── MeController.java            ← 기존 무변경
└── infrastructure/security/
    ├── SecurityConfig.java          ← 수정 (D-02/D-05 + returnTo success handler)
    ├── BaselineOidcUser.java        ← 기존 무변경
    └── BaselineOidcUserService.java ← 기존 무변경

src/test/java/com/anchors/baseline/auth/
├── BffAuthSessionIT.java            ← 신규 SC#1~4 단언 (또는 BffAuthIT 확장)
└── (BffAuthIT.java 수정)            ← logout() 헬퍼 경로 /logout→/api/auth/logout
```

### Pattern 1: 204 로그아웃 핸들러 — SecurityConfig DSL

**What:** `HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)`를 `logout()` DSL의 `logoutSuccessHandler()`로 교체. 기존 세션 무효화 로직은 그대로 유지.

**When to use:** SPA(fetch/XHR)가 302 redirect를 소화하지 못하는 BFF 패턴.

**Example:**
```java
// Source: classpath 실측 — HttpStatusReturningLogoutSuccessHandler(Spring Security 6.5.1)
.logout(logout -> logout
    .logoutUrl("/api/auth/logout")                     // /logout 에서 재지정
    .invalidateHttpSession(true)
    .clearAuthentication(true)
    .deleteCookies("SESSION")
    .logoutSuccessHandler(
        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
```

**주의:** `logoutSuccessHandler()` 를 설정하면 `defaultLogoutUrl`/`logoutSuccessUrl` 설정이 무시된다. [VERIFIED: classpath — LogoutConfigurer 소스]

### Pattern 2: session 엔드포인트 — nullable principal 분기

**What:** `@AuthenticationPrincipal OidcUser`는 비인증 요청에서 null이 주입된다(`errorOnInvalidType = false` 기본). `Map.of()`는 null 값 NPE → `LinkedHashMap` 또는 record DTO 사용.

**Example:**
```java
// Source: classpath 실측 — AuthenticationPrincipal.errorOnInvalidType default false
@GetMapping("/api/auth/session")
public SessionResponse session(@AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
        return new SessionResponse(false, null);
    }
    Object userId = principal.getAttribute("user_id");  // MeController 동일 패턴
    return new SessionResponse(true, userId);
}

// record DTO: userId nullable — 비인증 시 키 자체를 JSON에서 제외하려면 @JsonInclude 활용
// record SessionResponse(boolean authenticated, @JsonInclude(NON_NULL) Object userId) {}
// Map.of 금지(Phase 3 lesson): null 값 NPE
```

### Pattern 3: login 래퍼 + returnTo

**What:** `GET /api/auth/login?returnTo=/target`은 `/oauth2/authorization/{registration}` 302 리다이렉트를 발행한다. `returnTo`는 OIDC 성공 후 복귀 경로로 사용된다.

**returnTo 처리 메커니즘 (2가지 선택):**

**옵션 A (권장): oauth2Login successHandler에 targetUrlParameter 연결**

`SavedRequestAwareAuthenticationSuccessHandler.setTargetUrlParameter("returnTo")`를 oauth2Login successHandler로 설정한다. `GET /api/auth/login?returnTo=/somewhere`를 호출하기 전에 Spring Security의 `HttpSessionRequestCache.saveRequest()`가 요청을 저장하도록 하는 대신, `/api/auth/login` 컨트롤러가 직접 `/oauth2/authorization/{reg}?returnTo=/somewhere`로 리다이렉트한다.

그러나 `targetUrlParameter`는 콜백(`/login/oauth2/code/...`) 시점 요청에서 파라미터를 읽는다. `/api/auth/login`에서 `/oauth2/authorization/...`으로 가는 사이에 파라미터가 사라진다 → **옵션 A의 한계**: Spring Security OAuth2 authorization flow는 `returnTo`를 자동으로 전달하지 않는다.

**옵션 B (더 명확): returnTo를 세션에 저장 + 커스텀 successHandler**

```java
// AuthController.login():
// 1. returnTo 상대경로 검증
// 2. HttpSession에 "RETURN_TO" attribute 저장
// 3. redirect to /oauth2/authorization/{registration}

// SecurityConfig oauth2Login 커스텀 successHandler:
// 세션에서 "RETURN_TO" 꺼내 리다이렉트, 없으면 "/"
```

이 방식이 파라미터 소실 문제 없이 안전하다. [ASSUMED — returnTo 저장 방식. 구체 구현은 planner 확정]

**open-redirect 방지 — 직접 구현 필수:**

```java
// Source: classpath 실측 — UrlUtils.isValidRedirectUrl()은 절대URL도 허용
// Spring Security 자동 보호 없음 → 컨트롤러 직접 검증

private boolean isSafeRelativePath(String returnTo) {
    // 상대경로: /로 시작, //로 시작하지 않음(프로토콜 상대 URL 방지), http/https 없음
    return returnTo != null
        && returnTo.startsWith("/")
        && !returnTo.startsWith("//")
        && !returnTo.contains("://");
}
```

**registration 부재 가드:**
- 기존 `SecurityConfig`의 `ObjectProvider<ClientRegistrationRepository>` 가드가 oauth2Login DSL을 조건부 적용.
- `/api/auth/login` 컨트롤러가 `ClientRegistrationRepository`를 `@Autowired(required=false)` 또는 `ObjectProvider`로 주입받아, 없으면 적절한 응답(예: 503 Service Unavailable 또는 리다이렉트 to `/login`).
- **단, main 프로파일에서는 registration이 없어 oauth2Login이 비활성화** — 이 때 `/oauth2/authorization/test-idp`로 리다이렉트해도 404 반환. 테스트 프로파일에서만 실제 동작.

### Pattern 4: permitAll 매처 순서

Spring Security `authorizeHttpRequests` 규칙은 **선언 순서대로 첫 매칭** 적용.

```java
.authorizeHttpRequests(auth -> auth
    // 구체 경로 먼저 (permitAll)
    .requestMatchers("/actuator/health", "/login/**", "/oauth2/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/api/auth/session", "/api/auth/login")     // ← 신규 추가
        .permitAll()
    // 광역 패턴 나중 (401 엔트리포인트는 exceptionHandling에서 별도 설정)
    .anyRequest().authenticated())
```

`/api/auth/session`·`/api/auth/login`이 permitAll 목록에 있으면 `/api/**` 401 엔트리포인트 매처와의 충돌 없이 우선 적용된다. Spring Security는 `authorizeHttpRequests`와 `exceptionHandling.defaultAuthenticationEntryPointFor`를 독립적으로 처리한다 — permitAll 매처가 인증을 요구하지 않으므로 엔트리포인트 자체가 발동하지 않는다.

### Anti-Patterns to Avoid

- **컨트롤러에서 세션 직접 무효화:** `request.getSession().invalidate()`를 컨트롤러에 쓰지 않는다. 프레임워크 logout DSL이 `invalidateHttpSession(true)` + `clearAuthentication(true)` + `deleteCookies("SESSION")`을 원자적으로 처리 — 재발명은 안티패턴 (simplicity-first).
- **`Map.of(null-value)`:** `Map.of("userId", null)`은 NullPointerException. `LinkedHashMap`, `Collections.singletonMap`, record DTO 사용.
- **`/api/auth/logout` permitAll 추가:** 불필요. 로그아웃 핸들러는 인증 여부와 무관하게 동작하고, CSRF만 요구. permitAll로 두면 인증되지 않은 세션의 로그아웃 요청도 204로 처리되어 혼란.
- **open-redirect 미방지:** `returnTo=https://evil.com`을 그대로 리다이렉트하면 OWASP A01 취약점. 반드시 상대경로 검증.
- **`@AuthenticationPrincipal` 필수 처리(비null 가정):** `principal.getAttribute()` 직접 호출 시 비인증 요청에서 NPE. null 분기 필수.

---

## Don't Hand-Roll

| 문제 | 직접 구현 금지 | 사용 라이브러리 | 이유 |
|------|-------------|--------------|------|
| 로그아웃 세션 무효화·쿠키 삭제·인증 클리어 | 컨트롤러에서 `session.invalidate()` + `SecurityContext.clear()` 직접 구현 | `SecurityConfig logout() DSL` | Phase 3 `BffAuthIT.logoutInvalidatesSession()` 이 이미 행위 검증. 재발명은 검증 안 된 코드 도입 |
| CSRF 토큰 생성·쿠키 주입 | 커스텀 필터 직접 작성 | 기존 `CsrfCookieFilter` + `CookieCsrfTokenRepository` | Phase 3에서 이미 구현·검증됨 |
| OIDC 토큰 교환·id_token 검증 | 직접 IdP HTTP 호출 | Spring Security `oauth2Login()` DSL | OIDC 스펙(nonce/state/jwks 검증) 복잡도 방대 |

---

## Common Pitfalls

### Pitfall 1: logoutUrl 변경이 기존 테스트를 무음 파괴

**What goes wrong:** `logoutUrl("/logout")` → `/api/auth/logout` 변경 후, `BffAuthIT.logout()` 헬퍼가 여전히 `"/logout"`으로 POST를 보낸다. Spring Security는 경로 불일치 시 logout 처리를 하지 않고 `anyRequest().authenticated()` 규칙을 적용해 401을 반환한다(WireMock 인증 세션이 있으면 302 redirect 가능). 이 실패는 logout 자체보다 후속 테스트 단언(`assertThat(meStatus) == UNAUTHORIZED`)에서 잘못된 이유로 통과하거나 실패할 수 있어 진단이 어렵다.

**Why it happens:** `BffAuthIT.logout()` 헬퍼가 `baseUrl() + "/logout"`으로 하드코딩되어 있다.

**How to avoid:** `logoutUrl` 변경과 동시에 `BffAuthIT.logout()` 헬퍼의 경로를 `/api/auth/logout`으로 갱신. planner가 기존 테스트 경로 갱신을 별도 태스크로 명시할 것.

**Warning signs:** `logoutInvalidatesSession()` 테스트가 `meStatus == OK`(로그아웃이 동작하지 않은 상태)로 통과하면 logout URL 불일치 의심.

### Pitfall 2: `/api/auth/session` 비인증 시 401 — permitAll 누락 또는 매처 순서 오류

**What goes wrong:** `/api/auth/session`이 permitAll 목록에 없거나, `/api/**` 401 엔트리포인트 규칙 뒤에 선언되면 비인증 `GET /api/auth/session`이 200 대신 401을 반환한다.

**Why it happens:** Spring Security `authorizeHttpRequests`는 첫 매칭 규칙 적용. 광역 매처(`/api/**` 엔트리포인트)가 구체 매처보다 앞에 있으면 `/api/auth/session`도 광역 규칙에 포함될 수 있다. 단, `authorizeHttpRequests`와 `exceptionHandling.defaultAuthenticationEntryPointFor`는 독립적이고, permitAll 규칙이 있으면 인증 예외 자체가 발생하지 않아 엔트리포인트가 발동하지 않는다. 핵심은 **`/api/auth/session`을 `authorizeHttpRequests`의 permitAll 목록에 추가하는 것**이다.

**How to avoid:** 기존 `requestMatchers(...)` 목록에 `/api/auth/session`·`/api/auth/login` 추가. SC#4 단언으로 검증.

**Warning signs:** SC#4 테스트 — 비인증 `GET /api/auth/session` 시 401 반환.

### Pitfall 3: returnTo open-redirect

**What goes wrong:** `returnTo=https://evil.com`이 검증 없이 `sendRedirect(returnTo)`로 전달되면 사용자가 외부 악성 사이트로 리다이렉트된다.

**Why it happens:** Spring Security `UrlUtils.isValidRedirectUrl()`은 절대 URL도 유효하다고 판단한다(classpath 실측 확인). 자동 방어 없음.

**How to avoid:** 컨트롤러에서 `returnTo`가 `/`로 시작하고 `//`·`://`를 포함하지 않는지 검사. 무효한 `returnTo`는 무시하고 기본 경로(`/`)로 설정.

**Warning signs:** SC#3 open-redirect 거부 단언 실패.

### Pitfall 4: oauth2Login 비활성화 상태에서 /api/auth/login 리다이렉트 대상 없음

**What goes wrong:** main 프로파일에서 `ClientRegistrationRepository` 빈이 없어 oauth2Login DSL이 비활성화된다. 이 상태에서 `/api/auth/login`이 `/oauth2/authorization/test-idp`로 리다이렉트하면 `/oauth2/**` 경로를 처리하는 필터가 없어 404 또는 미처리 응답이 반환된다.

**Why it happens:** `ObjectProvider` 가드로 oauth2Login DSL 적용이 조건부. `SecurityConfig`의 기존 `WARN` 로그로 이미 알려진 상태.

**How to avoid:** `AuthController.login()`이 `ObjectProvider<ClientRegistrationRepository>`를 주입받아, registration 부재 시 503 또는 `/login`(기본 Spring Security 로그인 페이지)으로 응답. 테스트는 항상 `@DynamicPropertySource`로 registration을 주입하므로 IT에서는 발동하지 않음.

**Warning signs:** main 프로파일(`./gradlew bootRun`)에서 `/api/auth/login` 호출 시 404.

### Pitfall 5: `logoutSuccessHandler` 설정이 `deleteCookies`를 우회하지 않는지 확인

**What goes wrong:** `logoutSuccessHandler()`를 설정하면 **`logoutSuccessUrl()` 설정이 무시**된다(Spring Security `LogoutConfigurer` 소스 확인). 그러나 `invalidateHttpSession`, `clearAuthentication`, `deleteCookies`는 `LogoutHandler` 체인에서 처리되므로 `logoutSuccessHandler` 교체 영향 없음.

**Why it happens:** `logoutSuccessHandler`는 logout 처리 **완료 후** 응답 전송 단계만 교체하고, `LogoutHandler` 체인(세션 무효화·쿠키 삭제 등)은 별도 경로.

**How to avoid:** Phase 3 `BffAuthIT.logoutInvalidatesSession()`이 세션 삭제를 행위로 검증하므로, 해당 테스트가 패스하면 확인 완료. 별도 추가 검증 불필요.

---

## Code Examples

### logout() DSL 수정 (SecurityConfig)

```java
// Source: classpath 실측 — LogoutConfigurer.logoutSuccessHandler() + HttpStatusReturningLogoutSuccessHandler
.logout(logout -> logout
    .logoutUrl("/api/auth/logout")
    .invalidateHttpSession(true)
    .clearAuthentication(true)
    .deleteCookies("SESSION")
    .logoutSuccessHandler(
        new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
```

### session 엔드포인트 (AuthController)

```java
// Source: classpath 실측 — AuthenticationPrincipal errorOnInvalidType=false, null 주입
@GetMapping("/api/auth/session")
public SessionResponse session(@AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
        return new SessionResponse(false, null);
    }
    return new SessionResponse(true, principal.getAttribute("user_id"));
}

// record: userId null 허용, JSON 직렬화 시 null이면 키 제외(@JsonInclude 사용 권장)
record SessionResponse(boolean authenticated, @JsonInclude(JsonInclude.Include.NON_NULL) Object userId) {}
```

### login 래퍼 + returnTo (AuthController)

```java
// Source: classpath 실측 + ASSUMED(returnTo 세션 저장 방식)
@GetMapping("/api/auth/login")
public void login(
        @RequestParam(required = false) String returnTo,
        HttpSession session,
        HttpServletResponse response) throws IOException {
    // open-redirect 방지: 상대경로만 허용
    if (isSafeRelativePath(returnTo)) {
        session.setAttribute("RETURN_TO", returnTo);
    }
    // registration 부재 시 가드 (ObjectProvider)
    if (clientRegistrationRepository.getIfAvailable() == null) {
        response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
            "OIDC registration not configured");
        return;
    }
    response.sendRedirect("/oauth2/authorization/" + registrationId);
}

private static boolean isSafeRelativePath(String url) {
    return url != null && url.startsWith("/") && !url.startsWith("//") && !url.contains("://");
}
```

### oauth2Login successHandler — returnTo 복원 (SecurityConfig)

```java
// Source: classpath 실측 — SavedRequestAwareAuthenticationSuccessHandler
// ASSUMED: 세션 attribute 직접 읽기 방식(표준 targetUrlParameter 대신)
http.oauth2Login(oauth -> oauth
    .userInfoEndpoint(ui -> ui.oidcUserService(baselineOidcUserService))
    .successHandler((request, response, authentication) -> {
        HttpSession session = request.getSession(false);
        String returnTo = (session != null) ? (String) session.getAttribute("RETURN_TO") : null;
        if (session != null) session.removeAttribute("RETURN_TO");
        String target = (returnTo != null) ? returnTo : "/";
        response.sendRedirect(target);
    }));
```

---

## Runtime State Inventory

> Phase 5는 스키마 변경 없음. 이 섹션은 rename/refactor phase 전용 — 해당 없음(SKIPPED).

---

## State of the Art

| 구 방식 | 현재 방식 | 변경 시점 | 영향 |
|---------|----------|----------|------|
| `.csrf().spa()` | `CookieCsrfTokenRepository` + `CsrfCookieFilter` 직접 구현 | Spring Security 6.5.1에 `.spa()` 미존재 (Phase 3 lesson) | Phase 5는 기존 CsrfCookieFilter 재사용, 추가 조치 없음 |
| `logoutSuccessUrl()` (302 redirect) | `logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(NO_CONTENT))` | Phase 5 도입 | SPA/fetch 친화적 204 응답 |
| `authorizedClientRepository` 기본(InMemory) | `HttpSessionOAuth2AuthorizedClientRepository` 명시 (Phase 3) | Phase 3 도입 | 토큰이 Redis 세션에만 저장 — Phase 5 무변경 |

**Deprecated/outdated:**
- `logoutUrl("/logout")`: `/api/auth/logout`로 재지정(D-02). Phase 3 `BffAuthIT.logout()` 헬퍼도 갱신 필요.

---

## Assumptions Log

| # | 주장 | 섹션 | 오류 시 영향 |
|---|------|------|-----------|
| A1 | `returnTo` 세션 attribute 저장 방식(세션에 "RETURN_TO" 키 저장 → oauth2Login successHandler에서 복원)이 Phase 3 BaselineOidcUserService 흐름과 충돌 없음 | Code Examples (login 래퍼) | returnTo가 로그인 후 적용 안 되거나 세션 attribute 충돌 — planner가 BaselineOidcUserService 코드 확인 후 정합 결정 |
| A2 | `AuthController.login()`이 `ClientRegistrationRepository`를 `ObjectProvider` 또는 `@Autowired(required=false)`로 주입받는 방식이 ArchUnit `Interfaces → {Application, Domain}` 규칙을 통과 | Architecture Patterns (Pattern 3) | `ClientRegistrationRepository`는 `spring-security-oauth2-client` 타입 — infrastructure 계층 아님. ArchUnit이 어떻게 분류하는지 실행 시 확인 필요 |
| A3 | `registrationId`(login 래퍼에서 사용할 registration 이름)는 단일 registration 전제 하에 "test-idp" 또는 고정 값을 사용 | Pattern 3 | 다중 registration 환경에서 하드코딩 문제 — 단, CONTEXT.md D-04에서 단일 registration 전제 확인됨 |

---

## Open Questions

1. **`AuthController.login()`의 ArchUnit 통과 여부 — `ClientRegistrationRepository` 타입 위치**
   - 무엇을 아는가: `ClientRegistrationRepository`는 `spring-security-oauth2-client` 라이브러리 타입. `AuthController`는 `auth/interfaces` 패키지.
   - 불명확한 것: ArchUnit이 `spring-security-oauth2-client`를 infrastructure 계층으로 분류하지 않는다면(외부 라이브러리이므로 `..infrastructure..` 패턴 미매칭) `Interfaces`에서 직접 import 가능. 그러나 `ObjectProvider<ClientRegistrationRepository>` 방식은 Spring 타입만 사용하므로 안전.
   - 권장: `AuthController`에서 `ClientRegistrationRepository` 직접 사용 대신 `ObjectProvider`로 주입 — ArchUnit 계층 분류와 무관하게 안전. registration 존재 여부는 `getIfAvailable() != null` 체크.

2. **`returnTo`를 세션에 저장하는 방식 vs `SavedRequest` 메커니즘 활용**
   - 무엇을 아는가: `SavedRequestAwareAuthenticationSuccessHandler.setTargetUrlParameter("returnTo")`는 **콜백 요청**(`/login/oauth2/code/...`)에서 파라미터를 읽는다. `/api/auth/login?returnTo=/foo`에서 `/oauth2/authorization/test-idp`로 리다이렉트할 때 파라미터가 소실된다.
   - 불명확한 것: `OAuth2AuthorizationRequestRedirectFilter`가 authorize 요청을 저장할 때 쿼리 파라미터를 세션에 포함하는지.
   - 권장: 세션 attribute 방식(A1 가정) 사용이 더 명확하고 제어 가능. planner가 `BaselineOidcUserService` 또는 기존 oauth2Login successHandler 확인 후 확정.

---

## Environment Availability

> 외부 도구 의존성 없음(신규 런타임 도구 추가 없음). SKIPPED.

| 의존성 | 필요한 이유 | 사용 가능 | 버전 | Fallback |
|--------|-----------|----------|------|----------|
| PostgreSQL (Testcontainers) | IT 데이터 계층 | ✓ | postgres:16 (AbstractIntegrationTest) | — |
| Redis (Testcontainers) | 세션 read-back 단언 | ✓ | redis:7 (AbstractIntegrationTest) | — |
| WireMock standalone | MockOidcServer (OIDC 흐름) | ✓ | 3.13.1 (build.gradle.kts) | — |
| Docker (Testcontainers용) | 컨테이너 기동 | ✓ (프로젝트 사용 중) | — | — |

---

## Validation Architecture

### Test Framework

| 속성 | 값 |
|------|---|
| 프레임워크 | JUnit 5 + Spring Boot Test (`@SpringBootTest(RANDOM_PORT)`) + Testcontainers |
| 설정 파일 | `AbstractIntegrationTest.java` (싱글턴 컨테이너), `BffAuthIT.java` (`@DynamicPropertySource`) |
| 빠른 실행 | `./gradlew test --tests *BffAuth*` |
| 전체 실행 | `./gradlew test` |
| ArchUnit 실행 | `./gradlew test --tests *ArchitectureTest*` |

### Phase Requirements → Test Map

| REQ ID | 동작 | 테스트 타입 | 자동화 커맨드 | 파일 존재 여부 |
|--------|------|-----------|-------------|-------------|
| AUTH-06 비인증 | 비인증 `GET /api/auth/session` → `200 {authenticated:false}` (401 아님) | IT (MockMvc / RestTemplate) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| AUTH-06 인증 | 인증 상태 `GET /api/auth/session` → `200 {authenticated:true, userId:<id>}` | IT (oidcLogin post-processor 또는 WireMock 로그인 후) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| AUTH-07 204 | 인증 상태 `POST /api/auth/logout` (CSRF 포함) → `204 No Content` (302 아님) | IT (WireMock 세션 라운드트립) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| AUTH-07 세션삭제 | 로그아웃 후 동일 SESSION 쿠키 → 보호 API 401 + Redis `findById == null` | IT (WireMock — 서버측 상태 직접 read-back) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| AUTH-10 리다이렉트 | `GET /api/auth/login` → 302 to `/oauth2/authorization/{registration}` | IT (noRedirect RestTemplate) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| AUTH-10 returnTo | `GET /api/auth/login?returnTo=/somewhere` → 로그인 성공 후 `/somewhere`로 복귀 | IT (WireMock 전체 흐름) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| AUTH-10 open-redirect | `GET /api/auth/login?returnTo=https://evil.com` → returnTo 무시 또는 거부 | IT (MockMvc 단위) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| SC#4 매처순서 | `/api/auth/session`·`/api/auth/login` 비인증 접근 가능 AND `/api/me` 비인증은 401 | IT (MockMvc, no auth) | `./gradlew test --tests *BffAuthSession*` | ❌ Wave 0 신설 |
| D-06 ArchUnit | 신규 `AuthController`·`SecurityConfig` 변경이 기존 계층 규칙 통과 | ArchUnit (기존 `ArchitectureTest`) | `./gradlew test --tests *ArchitectureTest*` | ✅ 기존 파일 |
| D-02 회귀 | 기존 `BffAuthIT.logout()` 헬퍼 경로 `/logout` → `/api/auth/logout` 갱신 후 기존 `logoutInvalidatesSession()` 통과 | IT (기존 테스트 갱신) | `./gradlew test --tests *BffAuthIT*` | ✅ 경로만 수정 |

### SC별 단언 설계 상세

**SC#1 (AUTH-06):**

```java
// 비인증 — MockMvc 또는 RestTemplate (oidcLogin 없음)
GET /api/auth/session (no auth) → status 200, body.authenticated == false, no "userId" key
// 인증 — oidcLogin() post-processor (비용 절감, 상태 단언만)
GET /api/auth/session with oidcLogin(userId=<id>) → status 200, body.authenticated == true, body.userId == <id>
```

**SC#2 (AUTH-07) — WireMock 실 토큰 교환 필수 (Phase 3 lesson: 서버측 상태 read-back):**

```java
// 1. WireMock 로그인 → SESSION 쿠키 획득
// 2. POST /api/auth/logout + X-XSRF-TOKEN (CSRF) → assertThat(response.status == 204)
// 3. sessionRepository.findById(sessionId) → assertThat(result).isNull()  (Redis 세션 삭제 직접 확인)
// 4. GET /api/me with old SESSION cookie → assertThat(status == 401)
// 경로: BffAuthIT.logout() 헬퍼 "/logout" → "/api/auth/logout" 갱신
```

**SC#3 (AUTH-10) — 리다이렉트 + returnTo + open-redirect 방지:**

```java
// 리다이렉트 단언 (noRedirectRestTemplate 사용)
GET /api/auth/login → assertThat(response.status == 302)
                   → assertThat(response.headers.Location).contains("/oauth2/authorization/")

// returnTo 단언 (WireMock 전체 흐름 — 복귀 경로 검증)
performWireMockLogin(via /api/auth/login?returnTo=/dashboard)
→ 콜백 완료 후 최종 리다이렉트 Location == "/dashboard"

// open-redirect 거부
GET /api/auth/login?returnTo=https://evil.com
→ 리다이렉트 Location 이 "/dashboard" 또는 "/" (절대URL 무시)
GET /api/auth/login?returnTo=//evil.com
→ 리다이렉트 Location 이 "/"
```

**SC#4 (permitAll 순서):**

```java
// 비인증 접근 가능
GET /api/auth/session (no auth) → status 200 (not 401)
GET /api/auth/login (no auth) → status 302 (not 401)
// 기존 보호 API 불변
GET /api/me (no auth) → status 401
```

### 샘플링 레이트

- **태스크 커밋마다:** `./gradlew test --tests *BffAuthSession* --tests *BffAuthIT* --tests *ArchitectureTest*`
- **웨이브 머지마다:** `./gradlew test`
- **Phase 게이트:** 전체 테스트 GREEN 후 `/gsd:verify-work`

### Wave 0 갭

- [ ] `BffAuthSessionIT.java` — AUTH-06/07/10, SC#1~4 전체 커버 (신규 클래스, 또는 `BffAuthIT` 확장)
- [ ] `BffAuthIT.java` — `logout()` 헬퍼 경로 `/logout` → `/api/auth/logout` 수정 (회귀 방지)

---

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1` 적용.

### 적용 ASVS 카테고리

| ASVS 카테고리 | 적용 | 표준 제어 |
|-------------|------|---------|
| V2 인증 | yes | Spring Security oauth2Login — 기존 인증 흐름 재사용 |
| V3 세션 관리 | yes | Redis HttpSession + `invalidateHttpSession(true)` (로그아웃 시 세션 무효화) |
| V4 접근 제어 | yes | `authorizeHttpRequests` 매처 순서 — permitAll 범위 최소화 |
| V5 입력 유효성 | yes | `returnTo` 파라미터 — 상대경로 검증(open-redirect 방지) |
| V6 암호화 | no | 이 Phase에서 신규 암호화 없음 |

### 알려진 위협 패턴

| 패턴 | STRIDE | 표준 완화 |
|-----|--------|---------|
| Open Redirect (returnTo 미검증) | 스푸핑 | `isSafeRelativePath()` 상대경로 검증 — 절대 URL·프로토콜 상대 URL 거부 |
| CSRF on logout | 변조 | 기존 `CookieCsrfTokenRepository` + `X-XSRF-TOKEN` 헤더 — POST 로그아웃에 CSRF 토큰 필수 |
| Session fixation after login | 정보 노출 | Spring Security oauth2Login이 인증 성공 시 자동 세션 교체(`SessionManagementFilter`) |
| 인증 없는 세션 조회 노이즈 | 정보 노출 의도적 허용 | `/api/auth/session` permitAll + `{authenticated:false}` — SPA UX 설계 (AUTH-06 명세) |

---

## Sources

### Primary (HIGH confidence)

- classpath 실측: `spring-security-web-6.5.1-sources.jar` — `HttpStatusReturningLogoutSuccessHandler`, `SavedRequestAwareAuthenticationSuccessHandler`, `UrlUtils.isValidRedirectUrl()`
- classpath 실측: `spring-security-config-6.5.1-sources.jar` — `LogoutConfigurer.logoutSuccessHandler()`, `OAuth2LoginConfigurer` (AbstractAuthenticationFilterConfigurer 상속)
- classpath 실측: `spring-security-core-6.5.1-sources.jar` — `@AuthenticationPrincipal errorOnInvalidType = false`
- classpath 실측: `spring-security-web-6.5.1.jar` (jar 목록) — `HttpStatusReturningLogoutSuccessHandler.class`, `PathPatternRequestMatcher.class` 존재 확인
- 기존 코드: `SecurityConfig.java`, `MeController.java`, `BffAuthIT.java`, `MockOidcServer.java`, `AbstractIntegrationTest.java` — Phase 3 검증 자산

### Secondary (MEDIUM confidence)

- `CONTEXT.md` (05-CONTEXT.md) — D-01~D-07 자율 결정 + 근거 (비대화형 수집)
- `lessons/03-2026-05-31.md` — Phase 3 교훈 (Map.of NPE, .spa() 부재, framework default 검증 원칙)
- `libs.versions.toml` — Spring Boot 3.5.3, Spring Security 6.5.1 버전 확인 (`./gradlew dependencies` 실측)

### Tertiary (LOW confidence)

없음.

---

## Metadata

**신뢰도 분류:**
- Standard Stack: HIGH — classpath 실측 (jar 목록 + sources 확인)
- Architecture: HIGH — 기존 코드 분석 + Spring Security sources 확인
- Pitfalls: HIGH — Phase 3 lesson 실제 버그 기록 + classpath 실측 (UrlUtils 동작)
- returnTo 구현 방식: MEDIUM — 옵션 B(세션 저장) 권장이나 A1 가정 포함 (planner 확정 필요)

**Research date:** 2026-05-31
**Valid until:** 2026-06-30 (Spring Security 6.5.x 안정 — 빠른 변화 없음)
