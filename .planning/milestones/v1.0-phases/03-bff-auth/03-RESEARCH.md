# Phase 3: BFF 인증 - Research

**Researched:** 2026-05-31
**Domain:** Spring Security OAuth2/OIDC BFF 인증 + Spring Session Data Redis (서버 세션 토큰 보관)
**Confidence:** HIGH (스택·버전·ArchUnit 게이트 동작 모두 1차 소스로 실증)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** 인증 코드는 새 `auth` 바운디드 컨텍스트(`com.anchors.baseline.auth.{domain,application,infrastructure,interfaces}`). `common`에 두지 않는다. Spring Security 설정·핸들러·세션 설정 = `auth/infrastructure`. OIDC 성공→linkIdentity 조율(브리지) = `auth/application`. Spring Security 핸들러 인터페이스 구현체는 프레임워크 타입에 묶이므로 `infrastructure`에 두고 내부에서 `auth/application` 브리지를 호출.
- **D-02:** OIDC subject→로컬 User 매칭은 2단계. (1) `findByExternalId(oid)` 있으면 재로그인(linkIdentity 미호출, 멱등). (2) 없으면 IdP email로 INVITED 사용자 찾아 `linkIdentity(userId, oid, displayName)`→ACTIVE. 연결 후 매칭은 불변 식별자(oid)로만(A-6). 세션 principal 신원 키 = 로컬 `User.id`.
- **D-03:** `UserRepository`에 `Optional<User> findByEmail(Email)` 추가 필요(현재 `existsByEmail`만 존재). Identity 컨텍스트 안에서 최소 보강(포트 경유, 인증이 Identity 영속성 직접 접근 금지).
- **D-04 [가정]:** 이메일로도 INVITED 사용자 못 찾으면 인증 거부(invite-first, JIT provisioning 없음).
- **D-05:** Spring Session Data Redis로 HTTP 세션을 Redis에 영속화. `OAuth2AuthorizedClient`(토큰)는 `HttpSessionOAuth2AuthorizedClientRepository` 기반으로 세션→Redis 서버 측에만 저장. 토큰 쿠키/헤더/바디 노출 경로 안 만듦. [가정] 직렬화 포맷(JDK vs JSON), [가정] refresh 토큰 자동 갱신 구현 여부.
- **D-06:** 세션 쿠키 `HttpOnly=true`, `SameSite=Lax`, `Secure`(운영 프로파일). 쿠키명 Spring Session 기본 `SESSION`. CSRF는 기본 보호(`CookieCsrfTokenRepository`) 권장.
- **D-07:** 전 과정 MockMvc/`@SpringBootTest` + Testcontainers(Redis) 통합 테스트로 검증. 실 브라우저·실 IdP·실 포트 금지. SC#1·SC#4를 행위로 단언.
- **D-08 [가정]:** mock OIDC 전략 — (a) `oidcLogin()` 주입(인증 후 상태) (b) WireMock/MockWebServer로 OIDC discovery+token+jwks 스텁(토큰 교환 흐름까지). 권장: SC#1·SC#4는 (b) 수준, SC#2·SC#3은 (a) 수준 혼합.
- **D-09:** 의존성 추가 — `spring-boot-starter-oauth2-client`, `spring-session-data-redis`, (테스트)`spring-security-test`. NFR-01 — 그 외 인증 외부 의존 추가 금지.

### Claude's Discretion
`auth/infrastructure` 하위 세부 패키지명(`security`/`session`/`oidc`), `SecurityFilterChain` 빈 구성·엔드포인트 인가 규칙 표현, 세션 직렬화 포맷(JDK vs JSON), CSRF 구성 세부, 로그아웃 핸들러 구현 방식, mock OIDC 테스트 도구 선택, 인증 실패 응답 형태(401 JSON vs 리다이렉트), `findByEmail` 파생 쿼리 vs `@Query`, 마이그레이션 불필요 확인.

### Deferred Ideas (OUT OF SCOPE)
- 리프레시 토큰 자동 갱신 흐름 (SC는 "보관"만 요구)
- JIT(자동) 사용자 프로비저닝
- 서버 간 호출 자격증명(NFR-02 후단)
- `UserActivated` 이벤트 구독 (Phase 4)
- CORS 정책(별 오리진 프론트) — D-06은 동일 사이트 BFF 전제
- 권한 판단·역할·`PermissionEvaluator` (Phase 4)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AUTH-01 | OIDC IdP 로그인 (IdP 비종속) | `oauth2Login()` + `ClientRegistrationRepository`(application.yml provider/registration 외부화) — §Standard Stack, §Pattern 1 |
| AUTH-02 | 액세스/리프레시 토큰 브라우저 비노출, Redis 서버 세션에만 | `HttpSessionOAuth2AuthorizedClientRepository` + Spring Session Redis — §Pattern 2, §Pitfall 1·2 |
| AUTH-03 | 프론트는 쿠키 세션으로 통신 | Spring Session `SESSION` 쿠키, principal=로컬 User.id — §Pattern 3 |
| AUTH-04 | 로그아웃 시 서버 세션 무효화 | `logout()` → `SecurityContextLogoutHandler.invalidateHttpSession` → `SessionRepositoryFilter` Redis delete — §Pattern 4, §Pitfall 4 |
| AUTH-05 | 최초 로그인 시 Identity `linkIdentity` 호출 | 커스텀 `OidcUserService`(또는 `AuthenticationSuccessHandler`)→`auth/application` 브리지→`IdentityApplicationService.linkIdentity` — §Pattern 5, §Architectural Responsibility Map |
</phase_requirements>

## Summary

이 Phase는 **풍부한 도메인 모델이 아니라 프레임워크 와이어링 + 얇은 브리지**가 산출물의 본질이다(정본 §5.1). 세 가지를 조립한다: (1) Spring Security `oauth2Login()`으로 OIDC 인증, (2) Spring Session Data Redis로 HTTP 세션을 Redis에 영속화 — `OAuth2AuthorizedClient`(액세스/리프레시 토큰)는 기본 `HttpSessionOAuth2AuthorizedClientRepository`가 토큰을 세션 속성으로 저장하므로 자동으로 Redis 서버 측에만 들어가고 쿠키엔 `SESSION` ID만 나간다, (3) 최초 로그인 시 `auth/application` 브리지가 `identity.application.IdentityApplicationService.linkIdentity`를 호출.

**최대 위험 질문(D-01 [가정])은 실증으로 해소됐다:** ArchUnit `layeredArchitecture().whereLayer("Application").mayOnlyAccessLayers("Domain")`는 **같은 레이어(Application→Application) 의존을 위반으로 잡지 않는다.** ArchUnit 소스(`Architectures.java`의 `targetMatchesIfDependencyIsRelevant`)가 허용 대상에 `ownLayer`를 명시적으로 `.or()`로 더하기 때문이다 `[VERIFIED: ArchUnit main source]`. 두 점 패턴(`..application..`)상 `auth.application`과 `identity.application`은 둘 다 "Application" 레이어이므로 **`auth.application → identity.application` 호출은 게이트를 통과한다.** 브리지를 별도 포트로 우회하거나 게이트를 변경할 필요가 없다.

**두 번째 핵심 landmine:** Spring Session Redis의 **기본 직렬화는 JDK Java 직렬화** `[CITED: docs.spring.io/spring-session/reference/configuration/redis.html]`. baseline은 기본(JDK)을 그대로 쓰면 `OAuth2AuthorizedClient`/`OidcUser`가 그대로 직렬화돼 동작한다. **JSON(`GenericJackson2JsonRedisSerializer`)으로 바꾸면** OAuth2/OIDC 객체 역직렬화가 `SerializationException`/Jackson polymorphic type 오류로 깨지는 것이 다수 보고됨(spring-security #8373, #19077, spring-session #3510). 따라서 baseline 권장은 **JDK 직렬화 유지**(전환 시 `SecurityJackson2Modules`/`springSessionDefaultRedisSerializer` 빈 필요 — 비용 대비 SC 무관).

**Primary recommendation:** `auth/infrastructure/security`에 `SecurityFilterChain`(oauth2Login + logout + CSRF) 1개 빈 + `@EnableRedisHttpSession`(또는 Boot auto-config), 커스텀 `OidcUserService`가 `auth/application` 브리지를 호출해 linkIdentity·로컬 User.id를 principal에 싣는다. 직렬화는 JDK 기본 유지. 검증은 `AbstractIntegrationTest`(Redis Testcontainer) 상속 + `spring-security-test` `oidcLogin()`/WireMock 혼합.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OIDC authorization-code 흐름·토큰 교환 | API/Backend (`auth/infrastructure`) | — | 토큰 교환·검증은 서버에서만(BFF A-3). 브라우저는 관여 안 함 |
| 토큰 보관(`OAuth2AuthorizedClient`) | Database/Storage (Redis 세션) | API/Backend | NFR-02: Redis 서버 세션에만. 프레임워크 기본(`HttpSessionOAuth2AuthorizedClientRepository`)이 세션에 저장 |
| 세션 ID 쿠키(`SESSION`) | Browser/Client | API/Backend | 쿠키엔 세션 ID만. 토큰은 절대 쿠키에 없음 |
| SecurityFilterChain·핸들러·세션 설정 | API/Backend (`auth/infrastructure`) | — | 프레임워크 와이어링·횡단 인프라(D-01). 도메인 아님(§5.1) |
| 최초 로그인 신원 연결 조율(브리지) | API/Backend (`auth/application`) | Identity (`identity/application`) | 애플리케이션 수준 오케스트레이션. 도메인과 닿는 유일 지점 |
| INVITED→ACTIVE 전이·멱등 가드 | Identity 도메인(`identity/domain` — Phase 2 완료) | — | 전이 규칙은 `User` 애그리거트 내부. Phase 3은 호출만 |
| 로그아웃 세션 무효화 | API/Backend (`auth/infrastructure`) | Database/Storage (Redis) | `SecurityContextLogoutHandler`→`SessionRepositoryFilter`가 Redis 세션 delete |

## Standard Stack

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-oauth2-client | (BOM 3.5.3 → Security 6.5.1) | OIDC `oauth2Login`, `ClientRegistrationRepository`, `OAuth2AuthorizedClient` | 정본 §2 고정 인증 스택. BFF authorization-code 표준 경로 `[VERIFIED: Maven Central + Boot 3.5.3 BOM]` |
| spring-session-data-redis | 3.5.1 (BOM 3.5.3) | HTTP 세션을 Redis에 영속화 → 토큰이 세션 타고 Redis 서버 측에만 | NFR-02를 프레임워크 기본 동작으로 충족. Phase 1 Redis 인프라 재사용 `[VERIFIED: Maven Central + Boot 3.5.3 BOM]` |
| spring-boot-starter-security | (BOM 3.5.3 → Security 6.5.1) | `SecurityFilterChain`, CSRF, logout. oauth2-client가 전이 포함하나 명시 추가 가능 | 인증 필터 체인의 기반 `[VERIFIED: Maven Central]` |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| spring-security-test | 6.5.1 (BOM 3.5.3) | `SecurityMockMvcRequestPostProcessors.oidcLogin()/logout()/csrf()` | 인증 후 상태 테스트(SC#2·SC#3)에서 토큰 교환 우회 `[VERIFIED: Maven Central]` |
| spring-boot-starter-data-redis | (이미 존재, Phase 1) | Lettuce 연결 — 세션 저장소 백엔드 | 추가 불필요. spring-session-data-redis가 이 위에 동작 |

### mock OIDC 도구 (D-08, 테스트 전용 — 택1 또는 혼합)
| 도구 | 좌표 | 용도 | 비고 |
|------|------|------|------|
| WireMock | `org.wiremock:wiremock-standalone` (테스트 스코프) | OIDC discovery+token+jwks 엔드포인트 스텁 → authorization-code 토큰 교환 흐름 실증(SC#1 충실) | NFR-01: 테스트 전용 의존. [ASSUMED] 좌표·버전 — planner가 도입 시 verify |
| MockWebServer (okhttp3) | `com.squareup.okhttp3:mockwebserver` (테스트 스코프) | WireMock 대안. 더 경량 | [ASSUMED] — planner 재량 |
| `spring-security-test` oidcLogin() | (위 Supporting) | 토큰 교환 우회, 인증 후 상태만 주입 | 추가 의존 없음. SC#2·SC#3에 충분 |

> **권장:** SC#1(토큰 비노출)·SC#4(로그아웃)의 "토큰이 Redis에만"을 토큰 교환 경로까지 증명하려면 WireMock 수준이 필요(D-08 (b)). SC#2(쿠키 인증 API)·SC#3(linkIdentity 호출)은 `oidcLogin()`(a)로 충분. 비용 최소화하려면 (a) 우선 + SC#1 핵심만 (b).

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `HttpSessionOAuth2AuthorizedClientRepository`(세션 저장) | 커스텀 `OAuth2AuthorizedClientService`(Redis 직접 저장) | NFR-01 위반(커스텀 인프라). 기본이 이미 세션→Redis라 불필요 |
| JDK 직렬화 | JSON(`GenericJackson2JsonRedisSerializer`) | OAuth2/OIDC 객체 역직렬화 깨짐(다수 보고). SC 무관 비용. **금지 권장** |
| 커스텀 `OidcUserService`로 브리지 | `AuthenticationSuccessHandler` | 둘 다 가능. OidcUserService가 claims·principal 커스터마이징에 더 자연(§Pattern 5) |
| `@EnableRedisHttpSession` 명시 | Boot auto-config(spring-session-data-redis 존재 시 자동) | Boot가 클래스패스 보고 자동 구성. 명시는 namespace/repository 타입 제어 시만 |

**Installation (build.gradle.kts dependencies 블록):**
```kotlin
implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
implementation("org.springframework.session:spring-session-data-redis")
// spring-boot-starter-security 는 oauth2-client 가 전이 포함(명시 추가는 선택)
testImplementation("org.springframework.security:spring-security-test")
// WireMock 도입 시(D-08 (b)) — planner 확정 후
// testImplementation("org.wiremock:wiremock-standalone:<verify>")
```

**Version verification 결과 (실측):**
- Spring Boot BOM 3.5.3 관리 버전: **Spring Security 6.5.1**, **Spring Session 3.5.1**, **Spring Framework 6.2.8** `[VERIFIED: spring-boot-dependencies-3.5.3.pom]`
- `spring-session-data-redis`는 BOM이 버전 관리 → `version` 생략. group은 `org.springframework.session` (data-jpa 등과 다름 — `org.springframework.boot` 아님) `[VERIFIED: Maven Central 3.5.1 jar 존재]`

## Package Legitimacy Audit

> 전 의존성이 **Spring 공식 1차 아티팩트** + Spring Boot BOM 관리. 제3자 패키지 없음 → slopsquatting 위험 없음. 좌표를 Maven Central에서 직접 HTTP 200 + jar 존재로 실측.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| spring-boot-starter-oauth2-client:3.5.3 | Maven Central | 수년 | 대규모 | github.com/spring-projects/spring-boot | n/a (공식) | Approved (HTTP 200) |
| spring-session-data-redis:3.5.1 | Maven Central | 수년 | 대규모 | github.com/spring-projects/spring-session | n/a (공식) | Approved (HTTP 200, jar 확인) |
| spring-boot-starter-security:3.5.3 | Maven Central | 수년 | 대규모 | github.com/spring-projects/spring-boot | n/a (공식) | Approved (HTTP 200) |
| spring-security-test:6.5.1 | Maven Central | 수년 | 대규모 | github.com/spring-projects/spring-security | n/a (공식) | Approved (HTTP 200) |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none
**[ASSUMED] (planner verify before install):** WireMock/MockWebServer 좌표·버전 — D-08 (b) 채택 시에만. slopcheck는 Java/Maven 비대상이라 미실행; 모두 Spring 공식이므로 Maven Central 좌표 실측으로 대체.

## Architecture Patterns

### System Architecture Diagram

브라우저 로그인 요청은 백엔드가 IdP로 리다이렉트하고, authorization-code를 백엔드가 받아 서버에서 토큰 교환한다. 토큰은 `OAuth2AuthorizedClient`로 HTTP 세션에 저장돼 Spring Session을 타고 Redis 서버 측에만 들어간다. 브라우저로는 `SESSION` 쿠키(세션 ID)만 나간다. 최초 로그인이면 `OidcUserService`가 `auth/application` 브리지를 거쳐 Identity의 `linkIdentity`를 호출한다.

```
[Browser] --GET /oauth2/authorization/{idp}--> [auth/infra: SecurityFilterChain oauth2Login]
                                                         |
                                          302 redirect to IdP (authorize)
                                                         |
[Browser] <---- authorization-code ---- [IdP]
     |
     +--GET /login/oauth2/code/{idp}?code=...--> [Backend: token exchange (SERVER-side)]
                                                         |
                                       [OidcUserService.loadUser(claims)]
                                                         |
                                  resolve oid -> findByExternalId? --no--> findByEmail(email) -> INVITED
                                                         |                                          |
                                              (재로그인, 멱등)                    [auth/application bridge]
                                                         |                                          |
                                                         |                       [identity.application.linkIdentity] -> INVITED→ACTIVE
                                                         |                                          |
                                                         +<------------ principal carries local User.id ----+
                                                         |
                              [OAuth2AuthorizedClient(access+refresh tokens) stored in HttpSession]
                                                         |
                                       [SessionRepositoryFilter] -> serialize -> [Redis: spring:session:*]
                                                         |
[Browser] <---- Set-Cookie: SESSION=<id> (HttpOnly; SameSite=Lax; Secure-in-prod) -- ONLY session id, NO token

--- 후속 인증 API (SC#2) ---
[Browser] --Cookie: SESSION=<id>--> [SessionRepositoryFilter loads session from Redis] --> authenticated --> 200

--- 로그아웃 (SC#4) ---
[Browser] --POST /logout (+CSRF)--> [LogoutFilter: SecurityContextLogoutHandler.invalidateHttpSession]
                                          --> [SessionRepositoryFilter deletes Redis session] + Set-Cookie SESSION expired
[Browser] --Cookie: SESSION=<old id>--> [session not in Redis] --> 401/redirect
```

### Recommended Project Structure
```
src/main/java/com/anchors/baseline/auth/
├── domain/                       # 비거나 최소 (§5.1 — 인증은 도메인 모델 빈약)
├── application/
│   └── IdentityLinkService.java  # 브리지: oid/email→User.id 해소 + linkIdentity 조율
│                                 #   (identity.application.IdentityApplicationService + identity.domain.repository.UserRepository 주입)
├── infrastructure/
│   └── security/
│       ├── SecurityConfig.java               # @Configuration: SecurityFilterChain 빈
│       ├── BaselineOidcUserService.java       # extends OidcUserService → 브리지 호출 + principal에 User.id
│       └── (선택) SessionConfig.java          # 직렬화/namespace 커스터마이징 시만. 기본은 불필요
└── interfaces/
    └── (선택) MeController.java               # SC#2 검증용 인증 엔드포인트(/api/me). 테스트에 필요하면
```

> **주의(D-01):** `SecurityConfig`/`SecurityFilterChain`/`OidcUserService` 구현체는 프레임워크 타입 결합 → 반드시 `auth/infrastructure`. 브리지 서비스만 `auth/application`. 이렇게 두면 infrastructure→application(허용), application→domain/다른-context-application(허용, §ArchUnit) 모두 게이트 통과.

### Pattern 1: SecurityFilterChain (oauth2Login + logout + CSRF) — auth/infrastructure
**What:** 인증 필터 체인 1개 빈. OIDC 로그인·세션·로그아웃·CSRF를 선언.
**When to use:** Phase 3의 중심 와이어링.
**Example:**
```java
// Source: docs.spring.io/spring-security/reference/servlet/oauth2/login (Security 6.5.1)
// 위치: auth/infrastructure/security/SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http,
                                  OidcUserService baselineOidcUserService) throws Exception {
    http
      .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health", "/login/**", "/oauth2/**").permitAll()
        .anyRequest().authenticated())
      .oauth2Login(oauth -> oauth
        .userInfoEndpoint(ui -> ui.oidcUserService(baselineOidcUserService)))   // ← linkIdentity 브리지 지점
      .logout(logout -> logout
        .logoutUrl("/logout")
        .invalidateHttpSession(true)        // SC#4: Redis 세션 삭제 트리거 (기본 true)
        .clearAuthentication(true)
        .deleteCookies("SESSION"))          // 쿠키 즉시 만료
      .csrf(csrf -> csrf
        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));   // D-06: SPA 친화 CSRF
    return http.build();
  }
}
```
> **인증 실패 응답(D-08 재량):** SPA/HTTP 클라이언트면 미인증 시 302 IdP 리다이렉트 대신 401을 원할 수 있다. `.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))`로 API 경로에 401 반환. planner가 SC#2/SC#4 단언 형태(401 vs redirect)에 맞춰 결정.

### Pattern 2: 토큰의 Redis-only 보관 — 프레임워크 기본 동작 (코드 거의 없음)
**What:** `spring-session-data-redis`가 클래스패스에 있으면 Boot가 HTTP 세션을 Redis로 자동 구성. `oauth2Login`은 기본으로 `HttpSessionOAuth2AuthorizedClientRepository`를 써서 `OAuth2AuthorizedClient`(access/refresh 토큰)를 **세션 속성**으로 저장. → 세션이 Redis에 있으므로 토큰이 Redis에만 들어가고, 쿠키엔 `SESSION` ID만.
**When to use:** AUTH-02/SC#1. 추가 빈 불필요(NFR-01).
**핵심:** 토큰을 쿠키/헤더/바디로 내보내는 코드를 **만들지 않는 것**이 구현. "안 만든다"가 곧 충족.
```yaml
# application.yml — 세션 저장소를 Redis로 (Boot auto-config; 명시 옵션)
spring:
  session:
    store-type: redis            # spring-session-data-redis 있으면 자동이나 명시 가능
    redis:
      namespace: "spring:session:baseline"
  security:
    oauth2:
      client:
        registration:
          # IdP 비종속(AUTH-01) — 운영은 profile/secret로 외부화
        provider:
```
> **직렬화:** 기본 JDK 직렬화 유지(§Pitfall 1). `OAuth2AuthorizedClient`/`OidcUser`는 JDK Serializable이라 그대로 동작.

### Pattern 3: principal = 로컬 User.id (A-6) — 쿠키 세션 인증
**What:** 세션 principal이 IdP oid가 아니라 **로컬 `User.id`**를 들고 다녀 후속 Phase 4 인가가 참조.
**When to use:** SC#2/SC#3, A-6 계승.
**Example:** `OidcUserService`(Pattern 5)가 `DefaultOidcUser`에 로컬 User.id를 claim/attribute로 실어 반환하거나, User.id를 담은 커스텀 `OidcUser` 구현 반환. 후속 컨트롤러는 `@AuthenticationPrincipal`에서 User.id 추출.

### Pattern 4: 로그아웃 세션 무효화 (SC#4)
**What:** `logout()` → `LogoutFilter` → `SecurityContextLogoutHandler`가 `invalidateHttpSession=true`(기본)면 `HttpSession.invalidate()` 호출 → Spring Session의 `SessionRepositoryFilter`가 이를 가로채 **Redis에서 세션 delete** → 동일 쿠키 재요청 시 세션 부재로 인증 실패.
**When to use:** AUTH-04/SC#4.
**근거:** `SecurityContextLogoutHandler`는 기본 `invalidateHttpSession=true`, `clearAuthentication=true` `[CITED: SecurityContextLogoutHandler API docs]`. Spring Session 환경에서 `HttpSession.invalidate()`는 Redis 키 삭제로 위임됨 `[CITED: spring-boot #12634]`.
> `deleteCookies("SESSION")`로 응답에 만료 쿠키도 내려 브라우저 캐시 잔존 방지.

### Pattern 5: 최초 로그인 → linkIdentity 브리지 (AUTH-05/SC#3)
**What:** 커스텀 `OidcUserService`(권장) 또는 `AuthenticationSuccessHandler`가 OIDC subject→로컬 User 2단계 매칭(D-02)을 수행하고 INVITED면 브리지 통해 `linkIdentity` 호출.
**When to use:** AUTH-05. 도메인과 닿는 유일 지점(§5.1).
**Example:**
```java
// 위치: auth/infrastructure/security/BaselineOidcUserService.java
// OidcUserService 는 토큰 교환 후 UserInfo 처리 시점에 호출됨 — claims 접근 가능
@Component
@RequiredArgsConstructor
public class BaselineOidcUserService extends OidcUserService {
  private final IdentityLinkService linkService;   // auth/application 브리지

  @Override
  public OidcUser loadUser(OidcUserRequest req) {
    OidcUser oidc = super.loadUser(req);           // 표준 OIDC 처리
    String oid = oidc.getSubject();                // 불변 식별자(A-6)
    String email = oidc.getEmail();                // 표준 email claim [가정 D-03]
    String displayName = oidc.getFullName();
    Long userId = linkService.resolveAndLink(oid, email, displayName);  // ← 2단계 매칭 + linkIdentity
    // userId 를 principal 에 싣는다 (Pattern 3) — 커스텀 OidcUser 또는 attribute 추가
    return new BaselineOidcUser(oidc, userId);
  }
}
```
```java
// 위치: auth/application/IdentityLinkService.java  ← ArchUnit: Application→Application(identity) 허용(실증됨)
@Service
@RequiredArgsConstructor
@Transactional
public class IdentityLinkService {
  private final UserRepository userRepository;                 // identity.domain.repository (포트, D-03 findByEmail 추가)
  private final IdentityApplicationService identityAppService; // identity.application — 같은 레이어 허용

  public Long resolveAndLink(String oid, String email, String displayName) {
    var existing = userRepository.findByExternalId(oid);       // (1) 재로그인 — 멱등
    if (existing.isPresent()) return existing.get().getId();
    var invited = userRepository.findByEmail(new Email(email)) // (2) 최초 로그인 (D-03 추가 필요)
        .orElseThrow(() -> new AuthenticationException대체("미초대 사용자")); // D-04: 거부
    identityAppService.linkIdentity(invited.getId(), oid, displayName); // INVITED→ACTIVE
    return invited.getId();
  }
}
```
> **OidcUserService vs AuthenticationSuccessHandler:** `OidcUserService.loadUser`가 인증 *과정* 중(principal 확정 전)에 호출돼 principal 커스터마이징(User.id 주입)이 자연스럽다 `[CITED: Spring Security OAuth2 Login Advanced docs]`. SuccessHandler는 인증 *완료 후* 리다이렉트 제어용 — principal 교체엔 부적합. **권장: OidcUserService.** (D-01 명시대로 둘 다 infrastructure에, 브리지만 application.)

### Anti-Patterns to Avoid
- **토큰을 직접 Redis에 수동 직렬화/저장하는 커스텀 인프라:** NFR-01 위반. `HttpSessionOAuth2AuthorizedClientRepository` 기본이 이미 세션→Redis. 만들지 마라.
- **JSON Redis 직렬화로 전환:** OAuth2/OIDC 객체 역직렬화 깨짐(§Pitfall 1). SC 무관 비용.
- **브리지를 `auth/infrastructure`에 두기:** 가능은 하나 D-01이 조율은 application으로 규정. infrastructure에 두면 SecurityConfig가 비대해지고 재사용 골격의 의도(브리지=application) 흐려짐.
- **linkIdentity를 매 로그인마다 호출:** D-02 멱등 위반. `findByExternalId` 선조회로 재로그인 분기.
- **이메일로 연결 후에도 이메일로 매칭:** A-6 위반. 연결 후엔 oid로만.
- **principal에 IdP oid를 신원 키로:** A-6/IDEN-05 위반. 로컬 User.id를 키로.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 토큰 서버 보관 | 커스텀 Redis 토큰 스토어 | `HttpSessionOAuth2AuthorizedClientRepository`(기본) + Spring Session | 기본이 세션→Redis. NFR-01 |
| 세션 Redis 영속화 | 커스텀 세션 필터/직렬화 | `spring-session-data-redis`(Boot auto-config) | 검증된 `SessionRepositoryFilter` |
| OIDC authorization-code 흐름 | 수동 code→token HTTP 호출 | `oauth2Login()` | 검증·nonce·state·PKCE 처리 내장 |
| 로그아웃 세션 삭제 | 수동 Redis key delete | `logout().invalidateHttpSession(true)` | `SessionRepositoryFilter`가 Redis delete 위임 |
| CSRF 토큰 | 커스텀 토큰 발급/검증 | `CookieCsrfTokenRepository` | 세션 쿠키 기반 표준 |
| 인증 테스트 주입 | 수동 SecurityContext 세팅 | `spring-security-test` `oidcLogin()` | 토큰 교환 우회·표준 |

**Key insight:** 이 Phase의 가치는 "코드를 적게 쓰는 것". Spring Security/Session 기본 동작이 NFR-02를 충족하므로 **와이어링 + 얇은 브리지**가 전부다. 커스텀 인프라를 만드는 순간 NFR-01·§5.1 위반.

## Runtime State Inventory

> rename/refactor 아님(신규 컨텍스트 추가). 그러나 Phase 2 산출물 보강(D-03)·세션 저장소 활성화가 있어 런타임 영향 점검.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Redis에 새 `spring:session:*` 키가 생김(기존 Phase 1 Redis는 health check만 사용, 세션 미사용). 기존 데이터 충돌 없음 | 코드(세션 활성화)만 |
| Live service config | 없음. IdP registration/provider는 application-{profile}.yml로 외부화(미커밋 시크릿). 운영 IdP 등록은 배포 시 | none — invite-first, 테스트는 mock IdP |
| OS-registered state | 없음 — verified by 코드/설정만 변경 | none |
| Secrets/env vars | OIDC client-id/client-secret이 신규 env/secret로 필요(운영). baseline 테스트는 mock이라 불필요 | 운영 배포 시 secret 주입(코드 외) |
| Build artifacts | `spring-boot-starter-oauth2-client`/`spring-session-data-redis` 신규 클래스패스 진입 → 첫 빌드 시 의존성 resolve. egg-info류 없음(Gradle) | `./gradlew build` 재resolve |

**스키마 변경:** **불필요.** 세션은 Redis. D-03 `findByEmail`은 기존 `users` 테이블 조회 — 새 컬럼/테이블 없음. → **Flyway 마이그레이션 추가 없음** (verified: User 엔티티 email 컬럼 이미 존재, unique 제약 있음).

## Common Pitfalls

### Pitfall 1: Spring Session Redis JSON 직렬화로 전환 시 OAuth2/OIDC 객체 깨짐
**What goes wrong:** 직렬화를 `GenericJackson2JsonRedisSerializer`로 바꾸면 로그인 시 `SerializationException` 또는 Jackson "cannot resolve type id 'java.net.URL'"류 polymorphic type 오류. `DefaultOidcUser`/`OAuth2AuthorizationRequest`/`OidcUserAuthority` 체인이 화이트리스트 안 됨.
**Why it happens:** OAuth2/OIDC 객체는 JDK 직렬화 전제로 설계. JSON은 `SecurityJackson2Modules` 등록·polymorphic validator 화이트리스트가 추가로 필요.
**How to avoid:** **기본 JDK 직렬화 유지.** baseline은 SC가 직렬화 포맷 무관 → 전환 이득 없음. 굳이 JSON이면 `springSessionDefaultRedisSerializer` 빈에 `SecurityJackson2Modules.getModules(classLoader)` 등록.
**Warning signs:** 로그인 콜백(`/login/oauth2/code/*`)에서 500 + Jackson `InvalidTypeIdException`/`SerializationException`.
`[CITED: spring-security #8373, #19077; spring-session #3510; docs.spring.io/spring-session/configuration/redis]`

### Pitfall 2: `OAuth2AuthorizedClient` non-serializable 옛 이슈 오해
**What goes wrong:** 옛 자료(#5757 등)가 "OAuth2AuthorizedClient not serializable"이라 하나 이는 과거 버전. 현행 Security 6.5.1에선 `OAuth2AuthorizedClient`/`OAuth2AccessToken`/`OAuth2RefreshToken`이 `Serializable`.
**How to avoid:** JDK 직렬화 경로면 추가 작업 불필요. 통합 테스트(Redis Testcontainer)로 실제 직렬화 성공을 단언해 옛 가정에 발 묶이지 말 것.
**Warning signs:** `NotSerializableException` — 현행 버전에선 거의 안 나옴. 나오면 커스텀 principal 객체에 비직렬화 필드가 있는지 점검(예: Pattern 5의 `BaselineOidcUser`는 Serializable이어야 함).

### Pitfall 3: SameSite=Lax + 외부 IdP 리다이렉트 콜백 쿠키 누락
**What goes wrong:** `SameSite=Lax`에서 IdP→백엔드 콜백이 크로스사이트 top-level navigation(GET)이면 쿠키 전송되나, 일부 흐름(POST form_post response mode)에선 Lax가 쿠키를 막아 OAuth2 `authorization_request_not_found`/state 불일치.
**Why it happens:** authorization-code state는 인증 요청 세션에 저장 — 콜백에 세션 쿠키가 안 오면 state 복원 실패.
**How to avoid:** 표준 `code` response mode(GET 콜백)는 Lax로 동작. `form_post` 쓰면 `SameSite=None; Secure` 필요(별 오리진 = Deferred). baseline 동일 사이트 + code 흐름이면 Lax OK. 통합 테스트로 콜백 후 인증 성공 단언.
**Warning signs:** 콜백에서 `[authorization_request_not_found]` 또는 무한 리다이렉트.

### Pitfall 4: 로그아웃이 SecurityContext만 지우고 Redis 세션은 남김
**What goes wrong:** 커스텀 로그아웃을 잘못 구성해 `invalidateHttpSession`을 끄거나 Spring Session이 `HttpSession.invalidate()`를 가로채지 못하면 Redis 세션이 남아 동일 쿠키 재요청이 여전히 인증됨 → SC#4 실패.
**How to avoid:** 기본 `logout()` 사용(invalidateHttpSession 기본 true). 커스텀 핸들러를 쓰더라도 `SecurityContextLogoutHandler`를 체인에 유지. `deleteCookies("SESSION")` 추가. **반드시 SC#4를 "로그아웃 후 동일 쿠키 재요청 401" 행위로 테스트**(lesson 02 P1 — 주장이 아니라 테스트로).
**Warning signs:** 로그아웃 후 `/api/me`가 여전히 200.

### Pitfall 5: ArchUnit 게이트 — application→infrastructure 우발 의존
**What goes wrong:** 브리지(`auth/application`)가 Spring Security 타입(`OidcUser` 등 infrastructure스러운 타입)을 import하면 application→? 의존이 생길 수 있음. 단 Spring Security 타입은 `com.anchors.baseline` 밖이라 `consideringOnlyDependenciesInLayers`로 무시됨(게이트 무관).
**핵심 실증(D-01 [가정] 해소):** `auth.application → identity.application`은 둘 다 "Application" 레이어 → ArchUnit이 `ownLayer`를 허용 대상에 자동 포함하므로 **위반 아님** `[VERIFIED: ArchUnit Architectures.java targetMatchesIfDependencyIsRelevant — .or(ownLayer)]`. **브리지 포트 우회·게이트 변경 불필요.**
**How to avoid:** SecurityConfig·OidcUserService 구현체를 infrastructure에 유지. 브리지는 application. 계획 단계에서 `./gradlew test --tests "*ArchitectureTest*"`로 실증(lesson 01 P1 — 커밋된 게이트와 자기모순 금지).
**Warning signs:** ArchUnit RED with "Application ... Infrastructure" 위반(브리지가 infra 타입 직접 의존 시 — 단 baseline 패키지 한정).

### Pitfall 6: `findByEmail` 추가가 ArchUnit/JPA 파생 쿼리에서 깨짐
**What goes wrong:** D-03 `findByEmail(Email)`은 `Email`이 `@Embeddable`이라 파생 쿼리가 `email.value`로 풀려야 함. Phase 2 lesson에서 `existsByEmail(Email)` 파생 쿼리가 정상 해석됨이 실증됨(Pitfall 6 미발현) → `findByEmail`도 동일하게 동작 예상.
**How to avoid:** Phase 2 `UserJpaRepository`에 `Optional<User> findByEmail(Email email)` 추가 — `existsByEmail`과 동형. 깨지면 `@Query("select u from User u where u.email.value = :#{#email.value}")` fallback.
**Warning signs:** 부팅 시 `PropertyReferenceException: No property 'email' ... `.

## Code Examples

### 인증된 API 검증용 엔드포인트 (SC#2) — interfaces
```java
// 위치: auth/interfaces/MeController.java (SC#2 테스트가 호출할 보호 엔드포인트)
@RestController
public class MeController {
  @GetMapping("/api/me")
  public Map<String, Object> me(@AuthenticationPrincipal OidcUser principal) {
    return Map.of("userId", ((BaselineOidcUser) principal).getLocalUserId());  // 로컬 User.id (A-6)
  }
}
```

### SC#2/SC#3 테스트 — oidcLogin() (토큰 교환 우회)
```java
// Source: docs.spring.io/spring-security/reference/servlet/test/mockmvc/oauth2
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@Test
void authenticatedRequestSucceedsWithCookieSession() throws Exception {
  mvc.perform(get("/api/me").with(oidcLogin()
        .idToken(t -> t.subject("oid-123").claim("email", "invited@x.com"))))
     .andExpect(status().isOk());
}

@Test
void logoutInvalidatesSession() throws Exception {
  // 로그인 → 200 → POST /logout(+csrf) → 동일 세션 재요청 401
  mvc.perform(post("/logout").with(oidcLogin()).with(csrf()))
     .andExpect(status().is3xxRedirection());  // 또는 204, 구성에 따라
}
```
> **SC#1(토큰 비노출) 행위 단언(권장 (b) 수준):** WireMock으로 IdP token/jwks 스텁 → 실제 로그인 흐름 수행 → 응답 `Set-Cookie`에 `SESSION`만·토큰 문자열 부재 단언 + Redis 세션에서 `OAuth2AuthorizedClient` 존재 단언(`OAuth2AuthorizedClientService` 또는 세션 attribute 조회). `oidcLogin()`은 토큰 교환을 우회하므로 SC#1의 "토큰이 Redis에" 증명엔 (b)가 더 충실.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `RedisOperationsSessionRepository` | `RedisSessionRepository`(`@EnableRedisHttpSession`) / `RedisIndexedSessionRepository`(`@EnableRedisIndexedHttpSession`) | Spring Session 3.x | baseline은 기본(non-indexed)로 충분. indexed는 세션 이벤트·findByPrincipalName 필요 시만 |
| `OAuth2AuthorizedClient` non-serializable 이슈 | 현행 Serializable | Security 5.x→6.x | JDK 직렬화 경로 문제 없음(Pitfall 2) |
| 옛 `WebSecurityConfigurerAdapter` | `SecurityFilterChain` 빈 (component-based) | Security 5.7→6.x | baseline은 `SecurityFilterChain` 빈만 작성 |

**Deprecated/outdated:**
- `WebSecurityConfigurerAdapter`: 제거됨. `SecurityFilterChain` 빈 사용.
- `@EnableOAuth2Client`(spring-security-oauth, attic): 사용 금지. `oauth2Login()`/`oauth2Client()`로 대체됨.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | IdP가 표준 OIDC `email` claim 제공(D-03) | Pattern 5 | email 없으면 최초 로그인 매칭 불가 → 대체 claim/거부 정책 필요 |
| A2 | 미초대 사용자 로그인 거부(D-04, invite-first) | Pattern 5 | JIT 필요한 운영이면 SC 충돌 — planner가 ROADMAP 대비 확인 |
| A3 | JDK 직렬화 유지(D-05) | Pitfall 1·2 | 멀티앱 Redis 공유·버전 스큐 환경이면 JSON 필요 — 그땐 SecurityJackson2Modules 비용 |
| A4 | SameSite=Lax + code 흐름으로 콜백 쿠키 정상(D-06) | Pitfall 3 | form_post/별 오리진이면 None+Secure 재설계(Deferred) |
| A5 | refresh 토큰 자동 갱신 미구현(D-05 후단) | Summary | 액세스 토큰 만료 후 API 실패하면 갱신 흐름 필요 — Deferred 격상 |
| A6 | WireMock/MockWebServer 좌표·버전(D-08 (b)) | Standard Stack | 도입 시 Maven Central에서 좌표·버전 실측 필요 |

## Open Questions

1. **SC#1을 어느 충실도로 증명할 것인가 ((a) oidcLogin vs (b) WireMock 토큰 교환)?**
   - What we know: `oidcLogin()`은 토큰 교환 우회(principal만 주입), WireMock은 실제 code→token 흐름.
   - What's unclear: "토큰이 Redis에만"을 교환 경로까지 증명하려면 (b) 필요. (a)만으로는 세션에 mock OAuth2AuthorizedClient는 들어가나 "실제 IdP 교환 산물"은 아님.
   - Recommendation: SC#1·SC#4 핵심은 (b) WireMock 1개 테스트, 나머지(SC#2·SC#3)는 (a). planner가 비용 대비 확정.

2. **인증 실패 응답 형태 (401 JSON vs 302 IdP 리다이렉트)?**
   - What we know: 기본 oauth2Login은 미인증 시 IdP로 302. SPA/HTTP 클라이언트 테스트는 401이 단언하기 쉬움.
   - Recommendation: API 경로(`/api/**`)는 `HttpStatusEntryPoint(401)`, 브라우저 경로는 302. SC#2/SC#4 단언 형태와 일치시킬 것.

3. **principal에 User.id 싣는 방식 (커스텀 OidcUser vs attribute)?**
   - What we know: 커스텀 `BaselineOidcUser`(Serializable 필수) 또는 OidcUser attributes에 user_id 추가.
   - Recommendation: 커스텀 OidcUser가 타입 안전. 단 Serializable 보장(Pitfall 2).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Redis (Testcontainer) | 세션 통합 테스트 | ✓ | redis:7 (AbstractIntegrationTest) | — |
| PostgreSQL (Testcontainer) | findByEmail/linkIdentity 검증 | ✓ | postgres:16 (AbstractIntegrationTest) | — |
| Docker | Testcontainers | ✓(전제) | — (lesson: dockerApiVersion 옵트인 가드 존재) | — |
| 실 OIDC IdP | (운영만) | ✗ | — | mock(WireMock/oidcLogin) — D-07/D-08 |
| spring-boot-starter-oauth2-client | OIDC | ✗(추가 대상) | BOM 3.5.3 | — (D-09 추가) |
| spring-session-data-redis | 세션 | ✗(추가 대상) | 3.5.1 | — (D-09 추가) |

**Missing dependencies with no fallback:** D-09 스타터 3종 — 추가가 곧 Phase 작업(블로커 아님).
**Missing dependencies with fallback:** 실 IdP → mock(WireMock/oidcLogin).

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + spring-security-test 6.5.1 + Testcontainers 1.21.3 |
| Config file | build.gradle.kts (deps), AbstractIntegrationTest.java (Testcontainers base) |
| Quick run command | `./gradlew test --tests "*ArchitectureTest*"` (게이트 실증) |
| Full suite command | `./gradlew test` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AUTH-01 | OIDC 로그인 흐름이 인증 성공으로 귀결 | integration (WireMock IdP) | `./gradlew test --tests "*BffAuthIT*"` | ❌ Wave 0 |
| AUTH-02/SC#1 | 응답 쿠키에 `SESSION`만·토큰 부재 + Redis 세션에 토큰 존재 | integration (WireMock + Redis Testcontainer) | `./gradlew test --tests "*BffAuthIT.tokenOnlyInRedisNotInCookie*"` | ❌ Wave 0 |
| AUTH-03/SC#2 | 쿠키 세션만으로 `/api/me` 200 | integration (oidcLogin) | `./gradlew test --tests "*BffAuthIT.cookieSessionAuthenticatesApi*"` | ❌ Wave 0 |
| AUTH-04/SC#4 | 로그아웃 후 동일 쿠키 재요청 401/redirect | integration | `./gradlew test --tests "*BffAuthIT.logoutInvalidatesSession*"` | ❌ Wave 0 |
| AUTH-05/SC#3 | 최초 로그인 시 linkIdentity 호출 → INVITED→ACTIVE | integration (Postgres Testcontainer) | `./gradlew test --tests "*BffAuthIT.firstLoginLinksIdentity*"` | ❌ Wave 0 |
| AUTH-05 (멱등) | 재로그인 시 linkIdentity 미호출(이미 ACTIVE) | integration | `./gradlew test --tests "*BffAuthIT.reLoginIsIdempotent*"` | ❌ Wave 0 |
| 게이트 | `auth` 컨텍스트 4계층 의존 위반 0 | arch | `./gradlew test --tests "*ArchitectureTest*"` | ✅ (기존, 자동 적용) |

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "*ArchitectureTest*"` (계층 위반 즉시 RED — lesson 01 P1)
- **Per wave merge:** `./gradlew test --tests "*BffAuthIT*" --tests "*ArchitectureTest*"`
- **Phase gate:** `./gradlew test` 전체 GREEN

### Wave 0 Gaps
- [ ] `auth/.../BffAuthIT.java` (또는 분리) — SC#1~4·AUTH-01~05 행위 단언. `AbstractIntegrationTest` 상속.
- [ ] WireMock IdP 스텁 fixture(discovery/token/jwks) — D-08 (b) 채택 시. test 스코프 의존 추가.
- [ ] `findByEmail` 추가에 대한 Identity 측 단위/슬라이스 테스트(파생 쿼리 해석 — Pitfall 6).
- [ ] `BaselineOidcUser` Serializable 단언(Pitfall 2) — Redis 라운드트립 직렬화 테스트.
- [ ] 직렬화 라운드트립: 로그인 후 Redis 세션 직렬화/역직렬화 성공(Pitfall 1 회귀 가드).

## Security Domain

> `security_enforcement` 미설정 = 활성으로 간주. 이 Phase가 곧 보안 핵심(BFF 인증).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Spring Security OAuth2/OIDC `oauth2Login` (IdP 위임, 직접 비밀번호 처리 없음) |
| V3 Session Management | yes | Spring Session Data Redis(서버 세션), `HttpOnly`/`SameSite=Lax`/`Secure`(prod) 쿠키, 로그아웃 무효화 |
| V4 Access Control | no (Phase 4) | — (인증만, A-4 분리) |
| V5 Input Validation | partial | OIDC claim(email) 검증 — `Email` VO 형식 검증 재사용 |
| V6 Cryptography | no (위임) | 토큰 서명 검증은 Spring Security(jwks) — hand-roll 금지 |

### Known Threat Patterns for Spring OIDC BFF
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| 토큰 브라우저 노출(XSS 탈취) | Information Disclosure | BFF: 토큰 Redis 서버 세션에만, 쿠키 `HttpOnly`(NFR-02/SC#1) |
| CSRF (쿠키 세션) | Tampering | `CookieCsrfTokenRepository` + `SameSite=Lax` (D-06) |
| 세션 고정(session fixation) | Spoofing | Spring Security 기본 `sessionManagement` migrateSession (로그인 시 세션 ID 교체) |
| 미초대 사용자 무단 가입 | Elevation of Privilege | D-04 invite-first 거부(allowlist) |
| OAuth2 state/nonce 위조 | Tampering/Spoofing | `oauth2Login` 내장 state·nonce·PKCE 검증(hand-roll 금지) |
| 로그아웃 후 세션 잔존 | Spoofing | `invalidateHttpSession(true)` → Redis delete + `deleteCookies` (SC#4) |

## Sources

### Primary (HIGH confidence)
- ArchUnit `Architectures.java` (main) — `targetMatchesIfDependencyIsRelevant`가 `ownLayer`를 허용 대상에 `.or()`로 포함 → 같은 레이어 의존 허용 실증 (D-01 [가정] 해소)
- Spring Boot 3.5.3 `spring-boot-dependencies` POM — Security 6.5.1 / Session 3.5.1 / Framework 6.2.8 (실측)
- Maven Central — 4개 아티팩트 좌표 HTTP 200 + jar 존재 실측
- docs.spring.io/spring-session/reference/configuration/redis.html — 기본 JDK 직렬화, `springSessionDefaultRedisSerializer` 빈, namespace `spring:session`, `@EnableRedisHttpSession` vs Indexed
- docs.spring.io/spring-security/reference/servlet/test/mockmvc/oauth2 — `oidcLogin()` 토큰 교환 우회·SecurityContext 주입
- 코드베이스 직접 읽음: ArchitectureTest, AbstractIntegrationTest, IdentityApplicationService, UserRepository(findByEmail 부재 확인), User/Email, build.gradle.kts, libs.versions.toml, application.yml

### Secondary (MEDIUM confidence)
- SecurityContextLogoutHandler API docs — `invalidateHttpSession`/`clearAuthentication` 기본 true
- spring-boot #12634 — Spring Session 환경 `HttpSession.invalidate()` → Redis delete 위임
- Spring Security OAuth2 Login Advanced docs — `OidcUserService` 커스터마이징 지점

### Tertiary (LOW confidence — 검증 필요)
- spring-security #8373/#19077, spring-session #3510 — JSON 직렬화 시 OAuth2/OIDC 역직렬화 깨짐(다수 보고, baseline은 JDK 유지로 회피하므로 직접 검증 불요)
- WireMock/MockWebServer 좌표·버전 — [ASSUMED], 도입 시 실측

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 전 의존성 BOM-managed 버전·좌표 Maven Central 실측, 전부 Spring 공식
- Architecture (ArchUnit 게이트): HIGH — ArchUnit 소스로 같은-레이어 허용 실증(최대 위험 질문 해소)
- 직렬화 pitfall: HIGH — Spring Session 공식 문서로 JDK 기본 확인
- 토큰 Redis-only 메커니즘: MEDIUM-HIGH — 프레임워크 기본 동작(공식 문서) + 통합 테스트로 실증 필요
- mock OIDC 테스트 충실도: MEDIUM — oidcLogin() 공식, WireMock 흐름은 [ASSUMED] 좌표

**Research date:** 2026-05-31
**Valid until:** 2026-06-30 (안정 스택. Spring Boot 패치 시 BOM 버전만 재확인)
