# Phase 5: BFF 인증 세션 엔드포인트 - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

> **수집 방식 주석:** 이 CONTEXT.md는 비대화형(autonomous subagent) 세션에서 작성되었다.
> discuss-phase의 대화형 질문(AskUserQuestion) 대신, 각 그레이 영역의 결정을
> ROADMAP Success Criteria(SC#1~4) · REQUIREMENTS(AUTH-06/07/10) · PROJECT.md Constraints ·
> 정본 표준(`spring-backend-ddd-baseline.md` §3 A-3/A-4 · §5.1 · §6 NFR-02) ·
> Phase 3/4 코드·CONTEXT·lesson 교훈에 근거해 직접 내렸다. 사용자 확인 없이 자율 결정한 항목은
> 각 결정의 **[자율결정]** 태그 + 본 문서 말미 "Open Questions / Decisions" 표에 근거와 함께 기록했으니
> planner/사용자가 감사(audit)할 것.

<domain>
## Phase Boundary

BFF SPA가 **인증 흐름을 운용**하는 데 필요한 **순수 인증 HTTP 엔드포인트 3종**을 `auth/interfaces` 계층에 추가한다. 신규 도메인/애그리거트 없음 — 기존 `auth` 컨텍스트의 interfaces 표면 확장 + `SecurityConfig`(infrastructure) 조정만. 정본 §5.1대로 **인증·세션은 횡단 인프라이지 도메인 모델이 아니다** — 과한 DDD화 금지.

**핵심 경계: Identity/Authorization 컨텍스트 application 집계 의존이 일절 없다.** 그 교차 컨텍스트 집계(`/api/auth/me`, AUTH-08/09)와 ArchUnit 정비(AUTH-11)는 **Phase 6** 소관이다. Phase 5는 집계 없이 동작하는 순수 인증 흐름만 다룬다.

범위 (ROADMAP SC#1~4 / AUTH-06/07/10):

- **세션 상태 조회 (AUTH-06, SC#1):** `GET /api/auth/session`. 비인증 시 401이 아니라 `200 + {"authenticated": false}`를 반환(SPA가 401 노이즈 없이 로그인 여부 판별). 인증 시 `200 + {"authenticated": true, "userId": ...}`.
- **JSON 로그아웃 (AUTH-07, SC#2):** `POST /api/auth/logout`. 프레임워크 기본 302 redirect 대신 `204 No Content`. HttpSession 무효화 + `SESSION` 쿠키 삭제 + 인증 클리어. 이후 동일 세션 쿠키로의 보호 요청은 401.
- **OIDC 로그인 진입 래퍼 (AUTH-10, SC#3):** `GET /api/auth/login`. 프레임워크 `/oauth2/authorization/{registration}` 진입점을 감싸는 BFF 래퍼 + 로그인 후 복귀 경로(`returnTo`) 처리.
- **SecurityConfig permitAll 조정 (SC#4):** `/api/auth/session`·`/api/auth/login`을 permitAll 매처에 추가. 기존 보호 매처(`/api/**` 401 엔트리포인트)는 영향 없이 유지.

**범위 밖 (Phase 6 / Out of Scope):**

- `GET /api/auth/me` 신원·권한 집계 (AUTH-08/09) — **Phase 6**. Identity·Authorization application 교차 호출.
- ArchUnit 계층 의존 규칙 정비 (AUTH-11) — **Phase 6**. Phase 5는 ArchUnit **무변경**(아래 D-06 참조).
- 권한 변경 API(grant/revoke), JIT 프로비저닝, 리프레시 토큰 자동 갱신 — REQUIREMENTS Out of Scope / Phase 3 Deferred 계승.
- 프론트엔드/SPA 코드 — 베이스라인 백엔드 한정.
- 기존 `/api/me`(MeController) 제거/이전 — Phase 5는 신규 엔드포인트 추가만. `/api/me` legacy 처리는 건드리지 않음(D-01 참조).

</domain>

<decisions>
## Implementation Decisions

### 컨트롤러 배치 — 신규 AuthController 신설 ([자율결정])
- **D-01:** `/api/auth/*` 3종은 **신규 `auth/interfaces/AuthController`** 에 둔다. 기존 `MeController`(`/api/me`)는 **건드리지 않는다**.
  - **근거:** `MeController`는 Phase 3 SC#2 검증용 레거시 프로브(`/api/me` → `{userId}`)다. 신규 인증 표면은 응집도(cohesion) 기준으로 별도 컨트롤러가 자연스럽다 — `/api/auth/*` 경로 prefix가 일관되고, Phase 6의 `/api/auth/me`도 동일 컨트롤러에 자연 합류한다. MeController 확장은 경로 prefix 불일치(`/api/me` vs `/api/auth/*`)와 책임 혼선을 유발한다.
  - **ArchUnit 정합:** `AuthController`는 `auth/interfaces`에 속하므로 ArchUnit `Interfaces → {Application, Domain}` 만 허용. **infrastructure 타입(`BaselineOidcUser`) import/cast 금지** — MeController가 이미 따르는 패턴(`OidcUser.getAttribute("user_id")` 표준 읽기)을 그대로 계승한다. lesson 03 P1(framework default 검증)·04(third-party 누수)와 정합.
  - **[가정 — planner 확인]:** `MeController`/`/api/me`의 최종 운명(deprecate? Phase 6에서 `/api/auth/me`로 대체 후 제거?)은 Phase 6 결정. Phase 5는 공존시킨다.

### 로그아웃 구현 — 기존 Spring Security logout 머신 재사용, URL 재지정 + 204 핸들러 ([자율결정])
- **D-02:** `POST /api/auth/logout`은 **기존 `SecurityConfig`의 `logout()` DSL을 재사용**하되, `logoutUrl("/logout")` → `logoutUrl("/api/auth/logout")`로 **재지정**하고, 302 redirect 대신 **`204 No Content`를 쓰는 `LogoutSuccessHandler`**(또는 `HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)`)를 추가한다. 세션 무효화·쿠키 삭제·인증 클리어 로직을 **컨트롤러에서 직접 손으로 구현하지 않는다.**
  - **근거:** 기존 DSL이 `invalidateHttpSession(true)` + `clearAuthentication(true)` + `deleteCookies("SESSION")`을 이미 올바르게 수행하고, Phase 3 `BffAuthIT.logoutInvalidatesSession()`가 "로그아웃 후 401 + Redis 세션 삭제"를 **행위로 증명**한다(검증된 자산). 직접 구현은 검증된 프레임워크 동작을 재발명하는 안티패턴(simplicity-first). 단, 현재는 302를 반환하므로 SC#2(204 요구)를 위해 `LogoutSuccessHandler`만 교체한다.
  - **CSRF 정합 (결정적 제약):** 현재 CSRF가 켜져 있고(`CookieCsrfTokenRepository.withHttpOnlyFalse`), 로그아웃은 POST(상태 변경)라 **CSRF 토큰이 필요**하다. Phase 3 테스트가 `X-XSRF-TOKEN` 헤더 회신으로 이를 통과시킨다 — 신규 검증도 동일 패턴을 따라야 한다. **GET 로그아웃 금지**(CSRF 우회·SC#2 위반).
  - **[가정 — planner 검증]:** `logoutUrl` 변경이 Phase 3 `BffAuthIT.logout()` 헬퍼(`/logout` 호출)를 깨뜨린다. **기존 테스트의 로그아웃 경로도 `/api/auth/logout`으로 갱신**해야 한다(회귀 방지). planner가 기존 테스트 영향 범위를 태스크로 명시할 것. `/logout`을 별도로 살려둘지(이중 엔드포인트)는 불필요한 복잡도 — 단일 `/api/auth/logout`로 통일 권장.

### 세션 상태 응답 형태 (AUTH-06, SC#1)
- **D-03:** `GET /api/auth/session`은 **permitAll**(비인증 접근 가능)로 두고, principal 유무로 분기한다.
  - 비인증: `200 + {"authenticated": false}` (principal == null).
  - 인증: `200 + {"authenticated": true, "userId": <로컬 User.id>}`. `userId`는 `OidcUser.getAttribute("user_id")`(BaselineOidcUser가 병합한 로컬 PK, A-6)에서 읽는다 — MeController와 동일 출처.
  - **결정적 제약:** 이 엔드포인트가 401을 절대 던지지 않으려면 **permitAll 매처에 포함**되어야 하고(그렇지 않으면 `/api/**` 401 엔트리포인트에 걸림), 컨트롤러는 `@AuthenticationPrincipal OidcUser principal`을 **nullable**로 받아 null 분기해야 한다. `Map.of` 금지(null 값 NPE — lesson 03) → `LinkedHashMap`/record/`Collections.singletonMap` 사용.
  - **[자율결정 — 응답 직렬화]:** 응답 바디는 JSON object. `authenticated` boolean 필수, `userId`는 인증 시에만 포함(비인증 시 키 생략 권장 — SPA가 `authenticated`로 분기). record DTO vs Map은 planner 재량(스타일 일관성: MeController가 Map 사용 중이나 신규는 명시적 record/DTO 권장).

### OIDC 로그인 진입 래퍼 + returnTo (AUTH-10, SC#3)
- **D-04:** `GET /api/auth/login`은 **프레임워크 `/oauth2/authorization/{registration}` 진입점으로 302 리다이렉트**하는 얇은 래퍼다. authorization-code 흐름 자체는 Phase 3가 구현한 Spring Security가 수행 — 래퍼는 진입 + `returnTo` 보존만 담당.
  - **returnTo 전달 (결정적 설계점):** 로그인 후 복귀 경로는 Spring Security OAuth2가 인증 성공 후 `SavedRequest`(요청 캐시)나 명시적 파라미터로 복원한다. baseline 권장 메커니즘은 **`returnTo`를 세션/요청 캐시에 저장 → `AuthenticationSuccessHandler`(또는 기존 `BaselineOidcUserService` 흐름과 분리된 success handler)가 복원 시 리다이렉트**. planner가 Phase 3의 OIDC 성공 핸들러 구조와 정합하는 구체 메커니즘을 확정한다.
  - **[자율결정 — registration 선택]:** baseline은 단일 registration(`test-idp`는 테스트 전용)을 전제. 다중 registration 선택 UI는 범위 밖 — 래퍼는 단일/기본 registration으로 진입하거나 registration id를 경로/쿼리로 받는다(planner 확정). main 프로파일 registration 부재 가드(Phase 3 `ObjectProvider` 패턴)와 정합 유지.
  - **[자율결정 — returnTo 검증/보안]:** **open redirect 방지 필수.** `returnTo`는 **같은 사이트 상대경로만 허용**(절대 URL·외부 호스트 거부)해야 한다 — 그렇지 않으면 open-redirect 취약점. baseline 기본은 상대경로 화이트리스트/검증. planner가 검증 로직을 SC#3 단언과 함께 태스크화할 것. (Phase 3 D-06 동일 사이트 BFF 전제 계승.)

### SecurityConfig permitAll 매처 조정 (SC#4)
- **D-05:** `SecurityConfig.filterChain`의 `authorizeHttpRequests` permitAll 목록에 **`/api/auth/session`·`/api/auth/login`**을 추가한다. **`/api/auth/logout`은 추가하지 않는다**(로그아웃은 인증 흐름의 일부이나 permitAll로 둘 필요 없음 — 로그아웃 핸들러는 인증 여부와 무관하게 동작하고 CSRF만 요구).
  - **결정적 제약 (SC#4):** 기존 `/api/**` 401 엔트리포인트(`defaultAuthenticationEntryPointFor(401, "/api/**")`)는 **그대로 유지**. permitAll 매처가 `/api/auth/session`·`/api/auth/login`을 **더 구체적 매처로 선행**해야 `/api/**` 보호 규칙보다 우선 적용된다(Spring Security 매처 순서 — 구체 매처 먼저). planner가 매처 순서를 단언 테스트로 증명(`/api/me`는 여전히 401, `/api/auth/session`은 200).
  - **[자율결정 — login도 permitAll]:** `/api/auth/login`은 미인증 사용자가 호출하는 진입점이므로 **반드시 permitAll**(인증 요구 시 무한 루프). `/api/auth/session`도 미인증 200을 위해 permitAll. 이 둘만 추가하고 `/api/**` 나머지는 불변 — surgical change.

### ArchUnit — Phase 5 무변경 (AUTH-11은 Phase 6) ([자율결정])
- **D-06:** Phase 5는 **ArchUnit 게이트를 변경하지 않는다.** 신규 코드는 모두 기존 규칙을 자연 통과해야 한다:
  - `AuthController`(interfaces) → `OidcUser` 표준 읽기만, infrastructure 미참조 → `Interfaces → {Application, Domain}` 통과.
  - `SecurityConfig`(infrastructure)·`LogoutSuccessHandler`·로그인 success handler → infrastructure 내부 → 기존 규칙 통과.
  - **근거:** Phase 5는 Identity/Authorization application 교차 의존이 **없다**. AUTH-11(교차 컨텍스트 의존 허용 규칙 정비)은 그 집계가 필요한 Phase 6의 구조적 enabler다(STATE.md 결정 계승). Phase 5에서 ArchUnit을 건드리면 범위 침범 + lesson 01 P1(PLAN↔게이트 자기모순) 위험.
  - **결정적 제약:** planner는 신규 코드가 ArchUnit GREEN을 유지함을 `./gradlew test --tests *ArchitectureTest*`로 실증할 것(lesson 03/04 — 게이트 정합은 주장이 아니라 테스트로).

### 검증 전략 (SC#1~4 — Testcontainers/MockMvc, 라이브 구동 없음)
- **D-07:** 전 과정을 **`@SpringBootTest` + Testcontainers(postgres:16/redis:7) + WireMock(MockOidcServer)** 통합 테스트로 검증한다. 실 브라우저·실 IdP·라이브 포트 기동 금지(lesson: 8080/5432/6379 타 프로젝트 점유로 live run 차단).
  - **재사용:** `AbstractIntegrationTest`(싱글턴 Testcontainers) 상속. 기존 `BffAuthIT` 패턴(WireMock authorization-code 흐름 헬퍼 `performWireMockLogin`, `oidcLogin()` post-processor, 세션 쿠키/CSRF 유틸)을 그대로 재사용/확장.
  - **SC별 단언 설계:**
    - **SC#1(session):** 비인증 `GET /api/auth/session` → `200 {authenticated:false}` (no 401). 인증(oidcLogin 또는 WireMock 로그인) 후 → `200 {authenticated:true, userId:<로컬 id>}`.
    - **SC#2(logout):** 로그인(WireMock) → `/api/auth/logout` POST(+CSRF) → `204` + 동일 SESSION 쿠키 재요청 시 보호 API 401 + Redis 세션 삭제(`sessionRepository.findById == null`). Phase 3 `logoutInvalidatesSession` 패턴 계승·경로 갱신.
    - **SC#3(login):** `GET /api/auth/login?returnTo=/somewhere` → 302 to `/oauth2/authorization/{registration}` (또는 WireMock authorize) + returnTo가 로그인 후 복귀 경로로 전달됨을 단언. open-redirect 거부(절대 URL → 거부/무시) 단언 포함.
    - **SC#4(permitAll 순서):** `/api/auth/session`·`/api/auth/login` 비인증 접근 가능(no 401) **AND** `/api/me` 비인증은 여전히 401 — 매처 순서/스코프 회귀 방지.
  - **[자율결정 — mock 충실도]:** Phase 3 혼합 전략 계승 — 토큰/세션 라운드트립이 핵심인 단언(SC#2 로그아웃 세션 삭제)은 **WireMock 실 토큰 교환 경로**, 인증 후 상태만 필요한 단언(SC#1 인증 분기)은 **`oidcLogin()`** 으로 비용 절감. planner가 SC 충실도 대비 선택.

### Claude's Discretion (planner/researcher 재량)
- 응답 DTO 형태(record vs Map vs `LinkedHashMap`), `AuthController` 메서드 시그니처, `returnTo` 검증 구현(상대경로 정규식 vs allowlist), 로그인 래퍼의 registration 파라미터 방식(고정 vs 경로변수), `LogoutSuccessHandler` 구현(`HttpStatusReturningLogoutSuccessHandler` vs 커스텀), permitAll 매처 표현(`PathPatternRequestMatcher` 등), 신규 IT 클래스 분리 vs `BffAuthIT` 확장 — 모두 정본 표준·SC·ArchUnit 게이트·NFR 위반 없는 선에서 재량.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 정본 표준 (단일 진실 공급원)
- `spring-backend-ddd-baseline.md` (문서 v1.0) — 프로젝트 전체 정본. Phase 5 핵심 절:
  - §3 **A-3 (BFF 인증)** — 토큰 브라우저 비노출, 쿠키 세션 통신. 로그인 진입·세션 조회·로그아웃이 이 모델 위에서 동작 → AUTH-06/07/10 근거.
  - §3 **A-4 (인증/인가 분리)** — Phase 5=순수 인증만, 인가/권한 집계 없음(그건 Phase 6). 범위 경계의 1차 근거.
  - §5.1 **인증·세션 (과하게 DDD화하지 않는다)** — "BFF/OIDC/Redis 세션은 횡단 인프라, 도메인 아님." → 신규 엔드포인트는 얇은 interfaces + SecurityConfig 조정. 도메인 모델 신설 금지의 근거.
  - §6 **NFR-02 (보안)** — BFF 토큰 비노출. 로그아웃이 Redis 세션을 확실히 무효화해야 함(SC#2). NFR-01(외부 의존 최소화) — 신규 외부 라이브러리 금지(기존 security/session/oauth2 스타터만).

### 단계 정의 & 요구사항
- `.planning/ROADMAP.md` — Phase 5 정의, Goal, **Success Criteria 1~4**(session 200 분기, logout 204+세션무효, login 302+returnTo, permitAll 순서). Phase 6 경계(집계는 Phase 6).
- `.planning/REQUIREMENTS.md` — **AUTH-06 / AUTH-07 / AUTH-10**(Phase 5), AUTH-08/09/11(Phase 6 — 범위 밖), Traceability, Out of Scope.
- `.planning/PROJECT.md` — Constraints(스택·아키텍처 A-1~A-6 고정), Out of Scope(인가 변경 API·프론트), Key Decisions(방식 A·Flyway 불변성). Current Milestone v1.1.
- `.planning/STATE.md` — **누적 컨텍스트** 섹션: Phase 5/6 분할 결정, permitAll 매처 추가 대상(`/api/auth/session`·`/api/auth/login`), `/api/me`↔`/api/auth/me` 중복 주의. 이 CONTEXT가 그 결정을 구체화함.

### Phase 3/4 코드·계약·게이트 (반드시 정합)
- `src/main/java/com/anchors/baseline/auth/infrastructure/security/SecurityConfig.java` — **Phase 5 핵심 수정 대상.** 현재 permitAll 매처(actuator/login/oauth2/swagger), `logout()` DSL(`/logout`, invalidate+clear+deleteCookies SESSION, **현재 302 반환**), `/api/**` 401 엔트리포인트, CSRF(CookieCsrfTokenRepository + CsrfCookieFilter), `oauth2Login` registration 부재 가드(`ObjectProvider`). D-02/D-04/D-05 수정 위치.
- `src/main/java/com/anchors/baseline/auth/interfaces/MeController.java` — `/api/me` 레거시 프로브. `@AuthenticationPrincipal OidcUser` + `getAttribute("user_id")` 표준 읽기 패턴(infrastructure 미참조). D-01 신규 컨트롤러가 따를 형판. **Phase 5는 미변경.**
- `src/main/java/com/anchors/baseline/auth/infrastructure/security/BaselineOidcUser.java` — `user_id`(`USER_ID_ATTRIBUTE`) 키로 로컬 User.id 병합. session 응답 `userId` 출처(D-03).
- `src/test/java/com/anchors/baseline/architecture/ArchitectureTest.java` — 계층 게이트: `Interfaces → {Application, Domain}`, `Infrastructure → {Domain, Application}`. **Phase 5 무변경**(D-06) — 신규 코드가 자연 통과해야 함.
- `src/test/java/com/anchors/baseline/auth/BffAuthIT.java` — **검증 형판.** WireMock authorization-code 흐름(`performWireMockLogin`), `oidcLogin()`, 세션 쿠키/CSRF/Redis 세션 read-back 유틸. **D-02 logoutUrl 변경 시 이 테스트의 `logout()` 헬퍼 경로(`/logout`) 갱신 필요(회귀).**
- `src/test/java/com/anchors/baseline/auth/support/MockOidcServer.java` — WireMock OIDC discovery/token/jwks 스텁. SC#1~3 충실 검증에 재사용.
- `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` — 싱글턴 Testcontainers(postgres:16/redis:7). D-07 상속 대상.
- `build.gradle` — security/oauth2-client/session 스타터 Phase 3에서 추가됨. Phase 5는 **신규 의존 불필요**(NFR-01).

### Lesson 교훈 (재발 방지)
- `.planning/lessons/03-2026-05-31.md` — **P1: RESEARCH "프레임워크 기본" 주장은 실 classpath/테스트로 검증**(logout 핸들러·매처 순서 동작을 테스트로 증명). `Map.of` null NPE → `singletonMap`/record(D-03). 통합 phase에서 "test-only wave"가 main(SecurityConfig) 수정 유발 가능(D-02/D-05는 main 수정).
- `.planning/lessons/04-2026-05-31.md` — **🔴 Flyway 불변성**(Phase 5는 스키마 변경 없음 — 무관하나 원칙 유지). types-first 웨이브 분해 패턴 계승. third-party import 누수 주의(interfaces가 infrastructure 미참조).
- `.planning/milestones/v1.0-phases/03-bff-auth/03-CONTEXT.md` — Phase 3 인증 결정(D-01~D-09): auth 컨텍스트 구조, 세션/토큰 메커니즘, CSRF(D-06), mock OIDC 전략(D-07/08). Phase 5가 그 위에 엔드포인트를 얹는다.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`SecurityConfig.filterChain`** — permitAll 매처·logout DSL·401 엔트리포인트·CSRF가 모두 한 곳에 선언됨. Phase 5 수정은 이 빈 내부 surgical edit(매처 2개 추가, logoutUrl 재지정, LogoutSuccessHandler 추가).
- **`OidcUser.getAttribute("user_id")` 표준 읽기 패턴** (MeController) — session 응답 `userId` 추출에 그대로 재사용. infrastructure 타입 미참조 → ArchUnit 자연 통과.
- **`BffAuthIT` + `MockOidcServer` + `AbstractIntegrationTest`** — WireMock 실 토큰 교환·oidcLogin·Redis 세션 read-back·CSRF 회신 유틸이 완비. 신규 SC 단언이 이 인프라를 확장.
- **기존 logout 머신** — `invalidateHttpSession(true)`+`clearAuthentication(true)`+`deleteCookies("SESSION")`이 검증됨(`logoutInvalidatesSession`). 204 전환은 LogoutSuccessHandler만 추가하면 됨(세션 로직 재구현 불필요).

### Established Patterns
- **컨텍스트 = `auth/{infrastructure,application,interfaces}`** — 신규 컨트롤러는 `auth/interfaces`, SecurityConfig/핸들러는 `auth/infrastructure/security`. 도메인 빈약(§5.1).
- **interfaces는 표준 Spring Security 타입만 의존, infrastructure 구현 타입 미참조** — ArchUnit `Interfaces → {Application, Domain}` 정합(MeController 형판).
- **CSRF: 상태 변경(POST)은 X-XSRF-TOKEN 헤더 필요** — 로그아웃 검증은 Phase 3 CSRF 회신 패턴 따름.
- **types-first 선형 웨이브**(deps→impl→test) — 전방참조 컴파일 위험 제거(lesson 03/04).

### Integration Points
- **SecurityConfig permitAll 매처 순서** — `/api/auth/session`·`/api/auth/login`(구체)이 `/api/**` 401 엔트리포인트(광역)보다 우선 적용되어야 함. 매처 순서가 SC#4의 결정적 동작.
- **logoutUrl 변경의 회귀 면** — `/logout` → `/api/auth/logout` 변경이 Phase 3 `BffAuthIT.logout()` 헬퍼를 깨뜨림. 기존 테스트 경로 동반 갱신 필요.
- **OIDC 로그인 진입** — `/api/auth/login` 래퍼가 `/oauth2/authorization/{registration}`(Spring Security 진입점)으로 위임. registration 부재 가드(`ObjectProvider`) 동작과 정합.
- **returnTo 보존 경로** — Spring Security SavedRequest/요청 캐시 또는 명시 파라미터. Phase 3 OIDC 성공 핸들러 구조와 충돌 없이 복귀 리다이렉트 구현.

</code_context>

<specifics>
## Specific Ideas

- **AUTH-06의 핵심은 "401 노이즈 제거"** — SPA가 매 로딩마다 인증 여부를 확인할 때 401이 콘솔/네트워크 탭을 더럽히지 않도록 `/api/auth/session`이 **항상 200**을 반환하는 게 설계 의도(REQUIREMENTS.md). 비인증을 200으로 다루는 건 의도적 — 보호 API(`/api/**`)의 401 정책과 명확히 구분.
- **로그아웃은 JSON 흐름(204)** — 프레임워크 기본 302 redirect는 SPA(fetch/XHR)에 부적합. SC#2가 명시적으로 302 거부 + 204 요구.
- **§5.1 설계 철학 계승** — Phase 5 산출물의 본질은 "얇은 컨트롤러 3개 + SecurityConfig 조정"이다. 도메인 모델·애그리거트·VO 신설 금지. 과한 추상화는 lesson 04 "투기적 VO" 안티패턴.
- **골격 재사용성(Core Value)** — `/api/auth/*`는 후속 복제 프로젝트가 IdP 설정만 바꿔 그대로 쓰는 표준 인증 표면. SPA-친화적 응답 계약(200 session, 204 logout, 302 login)이 명료히 드러나야 함.

</specifics>

<deferred>
## Deferred Ideas

- **`GET /api/auth/me` 신원·권한 집계 (AUTH-08/09)** — **Phase 6.** Identity(userId/email/status) + Authorization(역할/메뉴/리소스 권한) 교차 컨텍스트 application 집계. Phase 5 범위 밖(집계 의존 없음).
- **ArchUnit 계층 의존 규칙 정비 (AUTH-11)** — **Phase 6.** `auth/interfaces`(또는 application)가 Identity·Authorization application 호출을 명시적으로 허용하는 규칙. AUTH-08/09 집계의 구조적 enabler. Phase 5는 ArchUnit 무변경.
- **`/api/me`(MeController) 정리** — Phase 6에서 `/api/auth/me`가 신원을 반환하면 `/api/me`(레거시 SC#2 프로브)와 중복. deprecate/제거 여부는 Phase 6 결정(STATE.md 중복 주의 항목).
- **다중 registration 선택 UI / IdP 디스커버리** — baseline 단일 registration 전제. 다중 IdP 선택은 복제 업무 프로젝트 영역.
- **리프레시 토큰 자동 갱신, JIT 프로비저닝, 서버 간 호출 자격증명, CORS(별 오리진 프론트)** — Phase 3 Deferred 계승. v1.1 범위 밖.

</deferred>

---

## Open Questions / Decisions (자율 결정 감사 로그)

> 비대화형 세션에서 AskUserQuestion 대신 자율 결정한 항목. planner/사용자가 감사할 것.

| # | 결정 | 선택 | 근거 | 신뢰도 |
|---|------|------|------|--------|
| 1 | 컨트롤러 배치 | 신규 `AuthController` 신설 (MeController 확장 아님) | `/api/auth/*` 경로 prefix 응집, Phase 6 `/api/auth/me` 자연 합류, MeController는 레거시 프로브. D-01 | [높음] |
| 2 | 로그아웃 구현 | 기존 logout DSL 재사용 + `logoutUrl`→`/api/auth/logout` + 204 LogoutSuccessHandler | 검증된 프레임워크 세션 무효화 재사용(재발명 금지), Phase 3 테스트가 행위 증명. 손수 구현은 안티패턴. D-02 | [높음] |
| 3 | 기존 `/logout` 유지 여부 | 단일 `/api/auth/logout`로 통일(이중 엔드포인트 X) — Phase 3 테스트 경로 동반 갱신 | 불필요한 복잡도 제거(simplicity-first). 단 회귀 면 존재 → planner가 기존 테스트 갱신 태스크화. D-02 [가정] | [중간] |
| 4 | session 응답 형태 | `{authenticated:bool}` + 인증 시 `userId`(키 생략 분기) | AUTH-06 명세 직접. `userId`는 `getAttribute("user_id")`. record/DTO 권장(Map.of NPE 회피). D-03 | [높음] |
| 5 | returnTo 보안 | open-redirect 방지 — 상대경로만 허용, 절대/외부 URL 거부 | 미검증 returnTo는 open-redirect 취약점. 동일 사이트 BFF 전제(Phase 3 D-06). D-04 [자율결정] | [높음] |
| 6 | permitAll 대상 | `/api/auth/session`·`/api/auth/login`만 추가, logout 제외, `/api/**` 401 불변 | login은 미인증 진입(필수 permitAll), session은 미인증 200. logout은 CSRF만 요구. surgical. D-05 | [높음] |
| 7 | ArchUnit | Phase 5 무변경 | 교차 컨텍스트 집계 의존 없음(AUTH-11은 Phase 6). 변경 시 범위 침범 + lesson 01 P1 위험. D-06 | [높음] |
| 8 | 검증 인프라 | Testcontainers + WireMock + MockMvc, 라이브 구동 없음 | 포트 혼잡(8080/5432/6379)으로 live run 차단. Phase 3 `BffAuthIT` 형판 재사용·확장. D-07 | [높음] |
| 9 | mock 충실도 | 세션 라운드트립 단언(logout)은 WireMock 실 토큰교환, 상태만 필요한 단언은 oidcLogin() | Phase 3 혼합 전략 계승 — SC 충실도 대비 비용. D-07 | [중간] |
| 10 | phase dir slug | placeholder `05-new-phase` 제거, `05-bff-auth-session` 신설 | placeholder 빈 디렉터리였음. sg-plan이 최종 정리. (Phase 명 "BFF 인증 세션 엔드포인트") | [중간] |

*Phase: 5-bff-auth-session*
*Context gathered: 2026-05-31*
