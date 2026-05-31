# Phase 3: BFF 인증 - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

> **수집 방식 주석:** 이 CONTEXT.md는 비대화형(autonomous) 세션에서 작성되었다.
> discuss-phase의 대화형 질문 대신, 각 그레이 영역의 결정을 ROADMAP Success
> Criteria(SC#1~4) · REQUIREMENTS(AUTH-01~05) · 정본 표준(`spring-backend-ddd-baseline.md`
> §2·§3 A-3/A-4/A-6·§5.1·§6 NFR-02) · Phase 1/2 코드·ArchUnit 게이트·lesson 교훈에
> 근거해 직접 내렸다. 사용자 확인이 필요한 가정은 각 결정의 **[가정]** 태그로 명시했으니
> planner가 표면화할 것.

<domain>
## Phase Boundary

브라우저에 액세스/리프레시 토큰을 **노출하지 않는 BFF(Backend-for-Frontend) 인증**을 동작시키고, **최초 로그인 시 Identity 컨텍스트의 `linkIdentity` 연산을 호출**해 INVITED→ACTIVE 전이를 일으킨다. 정본 §5.1이 못박은 대로 **인증·세션은 횡단 인프라이지 도메인 모델이 아니다** — 과하게 DDD화하지 않는다. 도메인과 닿는 유일한 지점은 신원 연결(linkIdentity) 호출이다.

범위 (ROADMAP SC#1~4 / AUTH-01~05):

- **OIDC 로그인 (AUTH-01):** Spring Security OAuth2/OIDC `oauth2Login`으로 IdP 비종속 로그인. authorization-code 흐름은 백엔드가 수행하고, 토큰 교환·검증은 서버에서만 일어난다.
- **토큰 서버 보관 (AUTH-02, SC#1, NFR-02):** 액세스/리프레시 토큰은 **Redis 서버 세션에만** 보관. HTTP 응답 쿠키에는 **세션 ID(`SESSION`)만** 나간다. 토큰은 절대 쿠키/바디/헤더로 브라우저에 노출되지 않는다.
- **쿠키 세션 통신 (AUTH-03, SC#2):** 프론트(또는 HTTP 클라이언트)는 세션 쿠키만으로 인증된 API를 호출한다. Spring Session + Redis로 세션을 서버 측에 영속화한다.
- **최초 로그인 신원 연결 (AUTH-05, SC#3):** OIDC 인증 성공 핸들러가 IdP subject(oid)를 로컬 `User`에 매핑하고, INVITED 상태면 `IdentityApplicationService.linkIdentity(userId, externalId, idpDisplayName)`를 호출해 ACTIVE로 전이시킨다. 이미 연결된(ACTIVE) 신원은 멱등(재호출 안 함).
- **로그아웃 세션 무효화 (AUTH-04, SC#4):** 로그아웃 시 Redis 서버 세션을 무효화하고, 이후 동일 세션 쿠키 요청은 인증 실패(401/리다이렉트)한다.

**범위 밖 (다른 Phase 소관 / Out of Scope):**

- 권한 판단·역할·그룹·`PermissionEvaluator` — **Phase 4 (Authorization)**. 정본 A-4(인증/인가 분리)에 따라 Phase 3은 **인증(신원 확인)만** 다룬다. 인가 결정은 일절 하지 않는다.
- Identity 도메인 모델 자체(`User`/VO/이벤트) — **Phase 2 완료**. Phase 3은 `IdentityApplicationService`를 **호출만** 한다(도메인 변경 없음).
- 관리자 invite REST API·프론트엔드 UI — 베이스라인 범위 밖(PROJECT.md Out of Scope: 프론트엔드/UI).
- 실제 외부 IdP(Entra ID/Keycloak/Okta) 운영 연동 설정값 — IdP 비종속이 원칙. 운영 IdP 등록은 배포 시 구성(profile/secret)으로, 검증은 mock/test IdP로 한다(D-08 참조).
- 서버 간 호출 자격증명(NFR-02 후단 "서버 간 호출은 별도 자격증명") — 현재 서버-서버 호출이 없으므로 미구현. 필요해지는 시점의 별도 작업.

</domain>

<decisions>
## Implementation Decisions

### 인증 코드의 패키지 위치 (ArchUnit 게이트 정합성 — lesson P1)
- **D-01:** 인증 코드는 **새 `auth` 바운디드 컨텍스트**(`com.anchors.baseline.auth.{infrastructure, application, interfaces}`)에 둔다. `common`에 두지 않는다.
  - **근거:** 정본 §5.1은 인증·세션을 "횡단 인프라"로 규정하지만, A-4(인증/인가 분리)와 §4.1(컨텍스트 경계)에 비추면 **인증은 식별·인가와 구분되는 독립 책임**이다. Phase 2가 `identity` 컨텍스트를 신설한 것과 동형으로, 인증은 `auth` 컨텍스트로 분리해 골격의 재사용 단위를 명확히 한다. `common`(범용 유틸/횡단 기술)에 도메인성 인증 흐름을 섞으면 컨텍스트 경계가 흐려진다.
  - **ArchUnit 정합 (결정적 제약):** `ArchitectureTest.hexagonalLayerDependencies()`는 `..domain..`/`..application..`/`..infrastructure..`/`..interfaces..` **두 점 패턴**으로 `com.anchors.baseline` 하위 **모든** 패키지를 매칭한다(`consideringOnlyDependenciesInLayers()`). 따라서 `auth` 컨텍스트도 4계층 규약을 그대로 따라야 하며, 계층 의존 위반 시 빌드 RED. 배치 규칙:
    - **Spring Security 설정(`SecurityFilterChain`/`SecurityConfig`), `ClientRegistrationRepository`, Spring Session 설정, OIDC 성공/로그아웃 핸들러** = 프레임워크 와이어링이자 횡단 인프라 → **`auth/infrastructure`** (또는 그 하위 `auth/infrastructure/security`).
    - **OIDC 성공 → linkIdentity 조율(브리지)** = 애플리케이션 수준 오케스트레이션 → **`auth/application`**. 단, Spring Security 핸들러 인터페이스(`AuthenticationSuccessHandler` 등) 구현체는 프레임워크 타입에 묶이므로 `infrastructure`에 두고, 그 안에서 `auth/application`의 브리지 서비스를 호출하는 구조를 권장한다.
  - **[가정 — planner 확정 필수]:** `auth/application`의 브리지 서비스가 `identity.application.IdentityApplicationService`를 의존하는 것은 **application → 다른 컨텍스트의 application** 의존이다. 현재 ArchUnit 게이트는 `Application`이 `Domain`만 접근 허용(`mayOnlyAccessLayers("Domain")`)한다. **`auth.application` → `identity.application` 호출이 이 규칙에 걸리는지 반드시 검증하라.** 두 점 패턴상 `identity.application`도 "Application" 레이어로 잡히므로 **Application→Application은 게이트 위반이 아니다(같은 레이어 간 접근은 차단 대상 아님)** — 단, `consideringOnlyDependenciesInLayers`/같은-레이어 규칙 해석을 planner가 실제 테스트(`./gradlew test --tests ArchitectureTest`)로 **실증**해야 한다. lesson P1(01-04 자기모순 재발 방지)이 정확히 이 지점을 겨눈다. 만약 위반이면 brige를 `identity` 컨텍스트가 노출하는 포트로 전환하거나 게이트 규칙을 명시적으로 보강한다(게이트 변경은 정본·D-05 정합 범위 내에서만).

### OIDC subject → 로컬 User 매칭 정책 (A-6 불변 식별자, SC#3)
- **D-02:** 매칭은 **2단계**다. (1) `UserRepository.findByExternalId(oid)`로 이미 연결된 사용자 조회 → 있으면 **재로그인**(linkIdentity 미호출, 멱등). (2) 없으면 **최초 로그인** → IdP 클레임의 이메일로 INVITED 사용자를 찾아 `linkIdentity(userId, oid, displayName)` 호출 → ACTIVE 전이.
  - **A-6 준수:** 연결 이후 매칭은 **불변 식별자(externalId=oid)** 로만 한다(이메일은 재할당 가능). 이메일은 **최초 연결 시점의 INVITED 사용자 해소**에만 쓰인다(정본 §5.2 "외부 ID는 인증 연결용 필드일 뿐, 업무 관계는 로컬 PK 참조").
  - **로컬 PK 매칭:** 인증 성공 후 세션 principal에 싣는 신원 키는 **로컬 `User.id`(불변 PK)** 다. 후속 Phase 4 인가가 이 `userId`를 참조한다(IDEN-05/A-6, 02-CONTEXT D-06 고정 원칙 계승).
- **D-03 (코드 갭 — planner 필수 해소):** 현재 `UserRepository` 포트에는 `findByExternalId(String)`은 있으나 **이메일로 INVITED 사용자를 찾는 조회(`findByEmail`)가 없다**(`existsByEmail`만 존재). 최초 로그인 매칭(D-02 2단계)을 위해 **`Optional<User> findByEmail(Email)` 추가가 필요**하다. 이는 Phase 2 산출물(`identity.domain.repository.UserRepository` + `identity.infrastructure.jpa.UserJpaRepository` 파생 쿼리)에 대한 **최소 보강**이며, Identity 컨텍스트 안에서 수행한다(인증이 Identity 내부 영속성을 직접 건드리지 않게 — 포트 경유).
  - **[가정]:** IdP 이메일 클레임 키는 표준 `email`. IdP가 `email`을 주지 않거나 verified=false인 경우 정책(거부/대체 클레임)은 planner가 결정. baseline은 표준 OIDC `email` 클레임을 전제로 한다.
- **D-04 (미초대 신원 정책 — [가정]):** 이메일로도 INVITED 사용자를 못 찾으면(= 초대받지 않은 사람이 로그인) **인증 거부**가 baseline 기본이다. 근거: 정본 §5.2 모델은 "외부에서 먼저 등록(invite)되고 나중에 로그인으로 연결"되는 흐름 — 자동 가입(JIT provisioning)은 명시되지 않았다. planner가 ROADMAP SC와 충돌 없는지 확인하고, 자동 가입이 필요하면 별도 결정으로 격상(스코프 주의).

### 세션·토큰 저장 메커니즘 (SC#1, AUTH-02, NFR-02)
- **D-05:** **Spring Session Data Redis**(`spring-session-data-redis`)로 HTTP 세션을 Redis에 영속화한다. OAuth2 클라이언트가 보관하는 `OAuth2AuthorizedClient`(액세스/리프레시 토큰 포함)는 **`HttpSessionOAuth2AuthorizedClientRepository`** 기반으로 **세션에** 저장되어, Spring Session을 타고 자연히 Redis 서버 측에만 들어간다. 토큰을 담은 쿠키·헤더·바디 노출 경로는 만들지 않는다.
  - **근거:** Phase 1이 이미 `spring-boot-starter-data-redis`를 깔았고 Redis를 세션 저장소로 구성하기로 했다(PLAT-05). Spring Session Redis가 NFR-02(토큰 비노출, Redis 서버 세션 보관)를 **프레임워크 기본 동작으로** 충족하는 가장 단순한 경로다. 토큰을 별도 직렬화해 Redis에 수동 저장하는 커스텀 인프라는 만들지 않는다(NFR-01 외부/커스텀 의존 최소화).
  - **[가정 — 직렬화]:** Spring Session Redis 세션 직렬화 방식(JDK 직렬화 기본 vs JSON)을 planner가 확정한다. `OAuth2AuthorizedClient`/`OidcUser`는 JDK 직렬화 호환이 기본이라 baseline은 **기본(JDK) 직렬화**를 전제하되, 보안/이식성 고려가 있으면 planner가 재검토. 여기서 토큰이 "Redis에만" 있음을 보장하는 게 핵심이지 직렬화 포맷은 부차적.
  - **[가정 — 토큰 보유 필요성]:** baseline 수준에서 리프레시 토큰으로 액세스 토큰을 **갱신**하는 흐름까지 구현할지는 SC에 없다(SC는 "Redis에만 저장"만 요구). 토큰을 **보관**하기만 하면 SC#1 충족. 자동 갱신(refresh)은 Deferred로 둘 수 있다 — planner가 SC 대비 필요 범위 확정.

### 세션 쿠키 구성 (SC#2, BFF 보안)
- **D-06:** 세션 쿠키는 **`HttpOnly=true`, `SameSite=Lax`, `Secure`(운영 프로파일에서)** 로 구성한다. 쿠키명은 Spring Session 기본 `SESSION`. `HttpOnly`로 JS 접근 차단(토큰 비노출 원칙의 연장선), `SameSite=Lax`로 기본 CSRF 완화.
  - **[가정]:** `SameSite`를 `Lax`로 둔다(같은 사이트 BFF 전제). 프론트가 별도 오리진이면 `None`+`Secure`+CORS·CSRF 정책 재설계가 필요 — 그 경우 planner가 ROADMAP 범위(프론트는 "쿠키 기반 세션") 내에서 결정. baseline 기본은 동일 사이트 BFF.
  - **CSRF:** 세션 쿠키 기반이므로 상태 변경 API에 CSRF 보호가 필요하다. baseline은 Spring Security 기본 CSRF(쿠키 토큰 방식 `CookieCsrfTokenRepository`)를 켜는 것을 권장하되, 정확한 구성은 planner 재량.

### 검증 전략 (SC#1·#4 — 실 브라우저/실 IdP 없이, lesson 환경 제약)
- **D-07:** 전 과정을 **MockMvc/`@SpringBootTest` + Testcontainers(Redis)** 통합 테스트로 검증한다. 실 브라우저·실 IdP·실 포트 기동은 쓰지 않는다(lesson: 로컬 8080/5432/6379 타 프로젝트 점유로 live run 차단 → Testcontainers/MockMvc 의존).
  - **SC#1(토큰 비노출) 단언:** 로그인 성공 응답의 `Set-Cookie`에 **`SESSION`만** 있고 토큰 문자열이 없음을 단언 + Redis 세션 저장소에 `OAuth2AuthorizedClient`/토큰이 **서버 측에** 존재함을 단언(세션 키 조회 또는 `OAuth2AuthorizedClientService`로). "쿠키엔 세션ID만, 토큰은 Redis에만"을 행위로 증명.
  - **SC#4(로그아웃 후 401) 단언:** 로그인 → 세션 쿠키로 인증 API 200 → 로그아웃 → **동일 쿠키 재요청 시 401/리다이렉트** 단언. Phase 2 lesson(행위 단언으로 격상)을 계승한다.
  - **Phase 1 자산 재사용:** `AbstractIntegrationTest`(싱글턴 Testcontainers PostgreSQL+Redis, 컨텍스트 캐시 공유)를 상속한다.
- **D-08 (mock OIDC 전략 — [가정], planner 확정):** 실 IdP가 없으므로 OIDC IdP를 **테스트에서 모킹**한다. 후보: (a) Spring Security Test의 `oidcLogin()`/`SecurityMockMvcRequestPostProcessors`로 인증 주체를 주입(인증 *이후* 상태를 검증 — 토큰 교환 흐름은 우회), (b) MockWebServer/WireMock으로 OIDC discovery+token+jwks 엔드포인트를 스텁(authorization-code 전 흐름까지 실증). SC#1의 "IdP를 통해 로그인 후 토큰이 Redis에"를 **토큰 교환 경로까지** 증명하려면 (b)가 더 충실하다. planner가 SC 충실도 대비 비용으로 (a)/(b)/혼합을 확정. baseline 권장: **핵심 토큰-비노출(SC#1)·로그아웃(SC#4)은 (b) 수준**, 인증 후 API 접근(SC#2)·linkIdentity 호출(SC#3) 단언은 (a) 수준 혼합.
  - 운영 IdP 설정은 `application-{profile}.yml`의 `spring.security.oauth2.client.registration/provider`로 외부화(IdP 비종속, AUTH-01). 시크릿은 환경변수/secret.

### 빌드 의존성 추가 (최소)
- **D-09:** 다음 스타터를 `build.gradle`에 추가한다(현재 없음, grep 확인): `spring-boot-starter-oauth2-client`(OIDC 클라이언트), `spring-session-data-redis`(Redis 세션). `spring-boot-starter-security`는 oauth2-client가 전이로 포함하나 명시 추가 가능. `spring-boot-starter-data-redis`는 Phase 1에 이미 존재. 테스트: `spring-security-test`. **NFR-01 준수 — 그 외 인증 관련 외부 의존(커스텀 토큰 스토어 등) 추가 금지.**

### Claude's Discretion (planner/researcher 재량)
- `auth/infrastructure` 하위 세부 패키지명(`security`/`session`/`oidc`), `SecurityFilterChain` 빈 구성·엔드포인트 인가 규칙 표현, 세션 직렬화 포맷(JDK vs JSON), CSRF 구성 세부, 로그아웃 핸들러 구현 방식(`LogoutSuccessHandler` vs 기본), mock OIDC 테스트 도구 선택(WireMock vs MockWebServer vs spring-security-test), 인증 실패 응답 형태(401 JSON vs 리다이렉트), `findByEmail` 파생 쿼리 vs `@Query`, 마이그레이션 불필요(스키마 변경 없음) 여부 확인 — 모두 정본 표준·SC·ArchUnit 게이트·NFR 위반 없는 선에서 재량.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 정본 표준 (단일 진실 공급원)
- `spring-backend-ddd-baseline.md` (문서 v1.0) — 프로젝트 전체 정본. Phase 3 핵심 절:
  - §2 기술 스택 — 인증=**Spring Security OAuth2/OIDC**(BFF, IdP 비종속), 세션/캐시=**Redis**(BFF 세션 저장소). 임의 대체 금지 — D-05/D-09 근거.
  - §3 **A-3 (BFF 인증)** — "액세스 토큰을 브라우저에 두지 않음. 토큰은 서버 세션(Redis)에만 보관, 프론트는 쿠키 세션으로 통신." → SC#1·#2·D-05·D-06의 1차 근거.
  - §3 **A-4 (인증/인가 분리)** — Phase 3=인증만, 인가는 Phase 4. 범위 경계의 근거.
  - §3 **A-6 (불변 식별자 기반 매칭)** — 연결 후 oid로 매칭, 이메일은 재할당 가능 → D-02 근거.
  - §5.1 **인증·세션 (과하게 DDD화하지 않는다)** — "BFF/OIDC/Redis 세션은 횡단 인프라, 도메인 아님. 도메인과 닿는 지점은 최초 로그인 시 신원 연결(`user.linkIdentity(oid)`)뿐." → D-01(인프라 배치)·SC#3 브리지의 핵심 근거. **이 Phase의 1차 설계 기준.**
  - §5.2 식별/온보딩 — `User`/`externalId`(불변)·로컬 PK 매칭. 인증이 호출할 Identity 계약. D-02·D-03 근거.
  - §6 **NFR-02 (보안)** — "BFF 패턴(토큰 비노출). 서버 간 호출은 별도 자격증명." → SC#1·D-05 근거. NFR-01(외부 의존 최소화) → D-09.

### 단계 정의 & 요구사항
- `.planning/ROADMAP.md` — Phase 3 정의, Goal, **Success Criteria 1~4**(토큰 Redis-only, 쿠키 세션 인증, linkIdentity 호출, 로그아웃 무효화).
- `.planning/REQUIREMENTS.md` — **AUTH-01 ~ AUTH-05**, Traceability.
- `.planning/PROJECT.md` — Constraints(스택·아키텍처 A-1~A-6 고정), Out of Scope(프론트/UI, 외부 IdP 전체 동기화).

### Phase 1/2 코드·계약·게이트 (반드시 정합)
- `src/test/java/com/anchors/baseline/architecture/ArchitectureTest.java` — **계층 의존 게이트(결정적 제약)**. `..domain../..application../..infrastructure../..interfaces..` 두 점 패턴이 신규 `auth` 컨텍스트에 **자동 적용**. `consideringOnlyDependenciesInLayers()`. D-01의 정합성 검증 대상.
- `src/main/java/com/anchors/baseline/identity/application/IdentityApplicationService.java` — **`linkIdentity(Long userId, String externalId, String idpDisplayName)`** 시그니처(로컬 userId를 받음 — 인증 브리지가 매핑 책임). SC#3 호출 대상.
- `src/main/java/com/anchors/baseline/identity/domain/repository/UserRepository.java` — 포트. `findById`, `existsByEmail`, **`findByExternalId(String)` 존재**, **`findByEmail` 부재(D-03 보강 대상)**.
- `src/main/java/com/anchors/baseline/identity/infrastructure/jpa/UserJpaRepository.java` — 파생 쿼리 어댑터(D-03 보강 위치).
- `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` — 싱글턴 Testcontainers(PostgreSQL+Redis). D-07 상속 대상.
- `build.gradle` — 현재 `spring-boot-starter-data-redis`만 있음. security/oauth2/session 스타터 부재(D-09 추가 대상).
- `.planning/phases/02-identity-context/02-CONTEXT.md` — D-06(로컬 PK 매칭 원칙 계승), lesson P1 정합 체크 계승.
- `.planning/lessons/01-2026-05-30.md` — **P1: PLAN ↔ 커밋된 ArchUnit 게이트 자기모순 금지**(D-01 [가정] 직결). 전방 참조 테스트 컴파일 교훈(타입 우선/선형 wave).
- `.planning/lessons/02-2026-05-31.md` — P1: "VALIDATION 주장 ↔ 실제 테스트 존재" 교차 확인(D-07 SC 단언이 실제 테스트로 구현됐는지). 환경: 로컬 포트 혼잡(8080/5432/6379) → live run 불가, Testcontainers/MockMvc 의존(D-07·D-08 근거). 선형 wave로 전방참조 위험 구조적 제거 패턴.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AbstractIntegrationTest`** — 싱글턴 Testcontainers(PostgreSQL+Redis, Spring 컨텍스트 캐시 공유). Phase 3 인증 통합 테스트가 그대로 상속(Redis 세션 검증에 Redis 컨테이너 필수).
- **`IdentityApplicationService.linkIdentity(userId, externalId, idpDisplayName)`** — Phase 3 OIDC 성공 브리지가 호출할 완성된 연산. 전이·멱등성 가드는 `User` 애그리거트 내부에 있으므로 브리지는 조율만.
- **`UserRepository.findByExternalId(String)`** — 재로그인 매칭(D-02 1단계)에 그대로 사용.
- **Redis 인프라(PLAT-05)** — `spring-boot-starter-data-redis` + Redis 컨테이너/compose가 이미 구성됨. 세션 저장소로 재사용.

### Established Patterns (auth 컨텍스트가 따를 형판)
- **컨텍스트 = `<context>/{domain,application,infrastructure,interfaces}`** (Phase 1 D-03 형판, Phase 2 `identity`가 적용). `auth`도 동일 규약. 단 인증은 도메인 모델이 빈약하므로(§5.1) `auth/domain`은 비거나 최소.
- **포트/어댑터** — 도메인/애플리케이션이 포트 정의, infrastructure가 구현. Spring Security 핸들러는 프레임워크 타입 결합 → infrastructure.
- **애플리케이션 서비스** — `@Service @RequiredArgsConstructor @Transactional`, 포트만 주입, 비즈니스 규칙 없음(조율만). auth 브리지 서비스도 동형.

### Integration Points & ⚠️ 게이트 제약 (lesson P1 — 위반 시 빌드 RED)
- **`ArchitectureTest`가 신규 `auth` 패키지에 자동 적용.** Application→Domain만 허용. **Application↔Application(같은 레이어) 간 접근은 차단 대상 아님** — 단 D-01 [가정]대로 `auth.application → identity.application` 호출을 planner가 `./gradlew test --tests *ArchitectureTest*`로 **실증**할 것. Spring Security `SecurityFilterChain`/핸들러/세션 설정은 infrastructure에 둬야 레이어 위반이 없다(infrastructure→domain/application 허용).
- **Phase 3가 소비하는 Phase 2 계약:** `linkIdentity` 시그니처·`UserRepository` 포트. D-03(`findByEmail` 추가)은 Identity 컨텍스트 내 최소 보강 — 인증이 Identity 영속성을 직접 건드리지 않게 포트 경유.
- **전방 참조 컴파일:** Phase 2 lesson대로 **선형 wave(인프라 설정 → 브리지 → 테스트)** 로 전방참조 위험을 구조적으로 제거. `@Disabled` 우회 불필요.
- **스키마 변경:** Phase 3은 세션을 Redis에 두므로 **새 RDB 테이블/Flyway 마이그레이션이 원칙적으로 불필요**(D-03 `findByEmail`는 기존 `users` 테이블 조회). planner가 확인.

</code_context>

<specifics>
## Specific Ideas

- 정본 §5.1이 이 Phase의 설계 철학을 한 문장으로 못박는다: "**인프라는 인프라로** — 모든 것을 애그리거트로 만들지 말 것." Phase 2(Identity)가 풍부한 도메인 모델이었던 것과 대비해, Phase 3는 **Spring Security/Session 프레임워크 구성 + 얇은 브리지** 가 산출물의 본질이다. 과한 추상화·도메인화 금지.
- 골격 재사용성(PROJECT Core Value): `auth` 컨텍스트는 후속 프로젝트가 "복제해서 IdP 설정값만 바꾸는" 세 번째 형판. SecurityFilterChain·세션·OIDC 브리지가 IdP 비종속으로 명료하게 드러나야 한다.
- SC#1이 baseline 보안의 핵심(NFR-02). "토큰이 쿠키에 없고 Redis에만"을 **테스트로 증명**하는 것이 이 Phase의 가장 중요한 검증 — 말로만 주장 금지(lesson 02 P1).

</specifics>

<deferred>
## Deferred Ideas

- **리프레시 토큰 자동 갱신 흐름** — SC는 토큰 "보관"만 요구(SC#1). 액세스 토큰 만료 시 리프레시로 자동 재발급하는 흐름은 SC 미요구 → baseline 미구현 가능. 토큰 수명·갱신이 실제 운영 이슈가 되는 시점에 추가(planner가 SC 대비 필요 범위 재확인 후 결정).
- **JIT(자동) 사용자 프로비저닝** — D-04 기본은 "초대 안 된 사용자 로그인 거부". 자동 가입이 필요한 운영 시나리오는 별도 결정/Phase(스코프 크리프 방지). 정본 §5.2 모델은 invite-first.
- **서버 간 호출 자격증명(NFR-02 후단)** — 현재 서버-서버 호출 없음. 마이크로서비스 분리·내부 API 호출이 생기는 시점에 별도 작업.
- **`UserActivated` 이벤트 구독** — Phase 2가 `UserActivated`를 미발행(02-CONTEXT Deferred). 최초 로그인 ACTIVE 전이 후속 처리(권한 효력 등)는 Phase 4(Authorization)에서 이벤트 기반 필요성 재검토.
- **CORS 정책(별 오리진 프론트)** — D-06은 동일 사이트 BFF(`SameSite=Lax`) 전제. 프론트가 별도 오리진으로 분리되면 CORS+`SameSite=None`+CSRF 재설계 필요 — 그 배포 형상이 확정되는 시점.

</deferred>

---

*Phase: 3-bff-auth*
*Context gathered: 2026-05-31*
