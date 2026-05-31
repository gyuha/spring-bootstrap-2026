# Phase 6: 내 정보 신원·권한 집계 - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

> **수집 방식 주석:** 이 CONTEXT.md는 비대화형(autonomous subagent) 세션에서 작성되었다.
> discuss-phase의 대화형 질문(AskUserQuestion)이 불가하여, 각 그레이 영역의 결정을
> ROADMAP Success Criteria(SC#1~3) · REQUIREMENTS(AUTH-08/09/11) · PROJECT.md Constraints ·
> 정본 표준(`spring-backend-ddd-baseline.md` §3 A-4/A-5 · §4.1 · §5.1) ·
> Phase 3/4/5 코드·CONTEXT·lesson 교훈 · **실제 소스 코드 실측**에 근거해 직접 내렸다.
> 사용자 확인 없이 자율 결정한 항목은 각 결정의 **[자율결정]** 태그 + 본 문서 말미
> "Open Questions / Decisions" 표에 근거·신뢰도와 함께 기록했으니 planner/사용자가 감사(audit)할 것.
> **특히 D-02(AUTH-09 권한 집계 범위 — 직접 부여 vs 상속 해석)는 신뢰도 [중간]의 핵심 결정이므로 우선 검토할 것.**

<domain>
## Phase Boundary

인증 사용자가 `GET /api/auth/me` **한 번**으로 자신의 **신원**(userId/email/status, Identity 컨텍스트)과 **권한 전체**(전역 역할·메뉴 권한·리소스 권한, Authorization 컨텍스트)를 **단일 응답**으로 받도록, `auth/interfaces`의 `AuthController`에 `me()` 메서드를 추가하고 두 컨텍스트의 **application 서비스를 교차 호출**해 집계한다. 신규 도메인/애그리거트 없음 — Phase 5에서 신설한 `AuthController`(`auth/interfaces`) 표면 확장 + 두 application 서비스에 **읽기(read) 메서드 신설**이 전부다. 정본 §5.1대로 인증/집계 표면은 얇은 interfaces 조율이지 도메인 모델이 아니다.

범위 (ROADMAP SC#1~3 / AUTH-08/09/11):

- **신원 집계 (AUTH-08, SC#1):** `GET /api/auth/me` → `userId`·`email`·`status`(Identity application에서 조회한 로컬 User). 비인증 시 **401**(permitAll 아님 — Phase 5의 `/api/auth/session`과 반대로 보호 매처 `/api/**`에 걸림).
- **권한 집계 (AUTH-09, SC#2):** **동일** `/api/auth/me` 응답에 전역 역할·메뉴 권한·리소스 권한(Authorization application에서 조회). 권한 없는 사용자는 빈 목록.
- **ArchUnit 계층 규칙 정비 (AUTH-11, SC#3):** `auth/interfaces`가 Identity·Authorization **application**을 호출하는 교차 컨텍스트 의존이 ArchUnit 계층 규칙으로 **명시 허용·GREEN 유지**됨을 보장.

**범위 밖 (Out of Scope / 후속):**

- 권한 변경 API(grant/revoke), 인가 REST 노출 — REQUIREMENTS Out of Scope(복제 업무 프로젝트 영역). Phase 6은 **읽기 집계만**.
- 신규 바운디드 컨텍스트, 새 도메인 모델/VO — interfaces 확장 + application read 메서드만.
- `/api/me`(MeController) 제거/deprecate — Phase 5 D-01에서 Phase 6 결정으로 이연됨. 본 phase 결정은 D-06 참조(공존 유지 권장).
- 프론트엔드/SPA 코드 — 베이스라인 백엔드 한정.

</domain>

<decisions>
## Implementation Decisions

### 신원 조회 — IdentityApplicationService에 read 메서드 신설 ([자율결정])
- **D-01:** Identity application에는 **현재 userId로 User 신원을 조회하는 읽기 메서드가 없다**(실측: `IdentityApplicationService`는 `invite`/`linkIdentity`/`disable` 쓰기 3종뿐). AUTH-08을 위해 **신규 read 메서드**를 `IdentityApplicationService`에 추가한다 — 예: `findUser(Long userId)`가 신원 프로젝션을 반환.
  - **근거:** `UserRepository.findById(Long)`(도메인 포트)는 존재하나 도메인 애그리거트 `User`를 반환한다. interfaces가 `User` 엔티티를 직접 받으면 (a) JPA 엔티티 누수, (b) interfaces→domain 의존 증가. application이 **읽기 DTO/프로젝션**으로 캡슐화해 반환하는 것이 헥사고날·CQRS-lite 정합(Phase 4 `EffectiveGrantDto` 패턴).
  - **반환 형태 [자율결정]:** application이 신규 **읽기 DTO**(예: `UserView(Long id, String email, String status)` record 또는 클래스)를 반환. `User` 엔티티/`Email` VO를 interfaces로 노출하지 않는다. `status`는 `UserStatus` enum의 `name()`(문자열) — SPA 친화. `User`는 `@Getter`로 `getId()/getEmail()/getStatus()` 노출, `Email`은 `getValue()` 노출(실측) → application에서 평탄화.
  - **미존재 사용자 처리 [자율결정]:** 인증된 principal의 `user_id`는 Phase 3 신원 연결로 항상 실 User에 대응한다(정상 흐름). 그래도 application read는 `findById` 결과가 비면 명확히 실패(예외 또는 빈 Optional)시킨다 — planner가 404 vs 500 매핑 확정. 정상 경로는 항상 존재.
  - **`@Transactional(readOnly = true)` [자율결정]:** 신규 read 메서드는 readOnly 트랜잭션. 현재 `IdentityApplicationService`는 클래스 레벨 `@Transactional`(쓰기) — read 메서드에 메서드 레벨 `@Transactional(readOnly=true)` 오버라이드 권장(planner 재량).

### 권한 조회 — AuthorizationApplicationService에 read 메서드 신설 + **집계 범위 = 직접 부여** ([자율결정] — 핵심)
- **D-02:** Authorization application에는 **userId로 "전역 역할 + 메뉴 + 리소스 권한 전체"를 조회하는 메서드가 없다**(실측). 존재하는 것:
  - `AuthorizationApplicationService` — grant/revoke/create **쓰기 전용**. 읽기 0건.
  - `AuthorizationPort.evaluate(userId, resource, action)`(boolean) / `listObjects(userId, action)`(List<Long>) — **특정 리소스/액션 단위 판정**. 전체 역할·메뉴 열거 불가.
  - `PermissionReadPort.findEffectiveGrants(userId, resourceId)`(리소스별) / `listAccessibleResourceIds(userId, action)`(액션별) — **전역 역할·메뉴를 열거하지 않음.**
  - 도메인 포트에 `GlobalRoleGrantRepository.findByUserId`, `MenuGrantRepository.findByUserId`, `ResourceGrantRepository.findByUserId`가 **존재**(직접 부여 조회 가능).
  - **결정:** AUTH-09를 위해 `AuthorizationApplicationService`에 **신규 read 메서드**(예: `findPermissions(long userId)`)를 추가하고, 위 3개 `findByUserId` 도메인 포트를 조율해 **직접 부여(direct grants)** 기반 권한 묶음 DTO를 반환한다.
  - **집계 범위 = 직접 부여(direct) [자율결정 — 신뢰도 중간, 최우선 감사 대상]:** AUTH-09 "권한 전체(전역 역할/메뉴/리소스)"를 **사용자에게 직접 부여된 grant**로 해석한다. **그룹 상속·리소스 계층 상속(재귀 CTE)은 포함하지 않는다.**
    - **근거:** (1) 단순성 — 검증된 `findByUserId` 3종 재사용, 신규 CTE 불필요. (2) 정본 §5.1 "과한 집계 표면 금지". (3) 기존 재귀 CTE(`PermissionReadPort`)는 **리소스별/액션별 파라미터**라 "사용자 전체 effective 권한"을 한 번에 열거하는 읽기 모델이 **부재** — 신설 시 상당한 신규 read 모델(전 리소스 cross-join CTE) 설계가 필요해 "내 정보 집계" 범위를 초과한다. (4) ROADMAP SC#2가 "전역 역할·메뉴 권한·리소스 권한" 3버킷을 직접 명시 — direct 3버킷과 정합.
    - **트레이드오프(명시):** SPA 메뉴/라우트 가드가 **그룹·계층 상속 권한**까지 필요로 하면 direct만으로는 불충분하다. 이 경우 planner는 (a) `GroupMemberRepository.findByUserId`로 소속 그룹의 group-scope grant까지 합산(중간 복잡도), 또는 (b) 신규 "user 전체 effective grants" CTE 읽기 모델 신설(고복잡도) 중 택해야 한다. **planner/사용자는 이 해석이 SPA 요구와 맞는지 먼저 확인할 것.** 본 결정은 baseline 최소 표면(direct)을 기본값으로 고정한다.
  - **반환 형태 [자율결정]:** 신규 권한 묶음 DTO. 3버킷 구조 권장 — `roles`(전역 역할 목록, 예: `["ADMIN","EDITOR"]`), `menus`(메뉴 권한: menuId+role), `resources`(리소스 권한: resourceId+role). `RoleName`/도메인 엔티티를 interfaces로 직접 노출하지 말고 application read DTO로 평탄화(role은 `name()` 문자열, Phase 4 평탄 프로젝션 패턴). 그룹 부여(groupId 기반 MenuGrant/ResourceGrant)는 direct 해석에서 **제외**(userId 기반 grant만).
  - **`@Transactional(readOnly = true)` [자율결정]:** 클래스 레벨 쓰기 `@Transactional` → read 메서드 readOnly 오버라이드.

### me() 응답 DTO — Phase 5 record + @JsonInclude 패턴 계승 ([자율결정])
- **D-03:** `me()` 응답은 **단일 record DTO**(예: `MeResponse`)로, 신원(userId/email/status) + 권한(roles/menus/resources)을 **하나의 응답에 결합**한다(ROADMAP SC#2 "단일 응답", PROJECT.md "한 응답 결합은 의도적"). Phase 5 `SessionResponse` record + `@JsonInclude` 패턴 계승(lesson 05 — Map.of NPE 회피, record 정공법).
  - **null/빈 처리 [자율결정]:** 권한 없는 사용자는 `roles=[]`, `menus=[]`, `resources=[]` **빈 목록**(null 아님 — SPA가 length 분기). 신원 필드는 항상 존재(인증 사용자). 빈 컬렉션은 직렬화 포함(빈 배열 노출), nullable 스칼라만 `@JsonInclude(NON_NULL)`.
  - **DTO 배치 [자율결정]:** `MeResponse`(+중첩 권한 record)는 `auth/interfaces`에 둔다(응답 계약은 interfaces 소유). application read DTO(`UserView`/권한 DTO)는 각 컨텍스트 application에 둔다. interfaces가 두 application DTO를 받아 `MeResponse`로 조립.

### userId 출처 — OidcUser.getAttribute("user_id") 표준 읽기 계승 ([자율결정])
- **D-04:** `me()`는 `@AuthenticationPrincipal OidcUser principal`에서 `getAttribute("user_id")`(로컬 User.id, A-6, `BaselineOidcUser` 병합)로 userId를 읽는다 — MeController/AuthController.session() 동일 출처. **infrastructure 타입(`BaselineOidcUser`) import/cast 금지**(ArchUnit interfaces→infrastructure 자연 통과, lesson 03/05 계승).
  - **타입 주의 [자율결정]:** `user_id` attribute는 `Long`(또는 Number)로 병합됨(Phase 5 테스트 `claim("user_id", 42L)` 실측). application read 메서드는 `long`/`Long`을 받으므로 interfaces에서 `Number.longValue()` 또는 안전 캐스팅. null principal은 SecurityConfig가 401로 차단(아래 D-05)하므로 me() 본문은 인증 전제.

### 보안 매처 — /api/auth/me는 permitAll 아님 (보호 유지) ([자율결정])
- **D-05:** `/api/auth/me`는 **permitAll에 추가하지 않는다.** 기존 `/api/**` 보호 매처 + 401 엔트리포인트에 그대로 걸려 비인증 시 **401**(SC#1 "비인증 시 401")이 자연 충족된다. Phase 5의 `/api/auth/session`(permitAll 200)과 **의도적으로 반대** — me는 신원/권한 노출이라 인증 필수.
  - **SecurityConfig 변경 [자율결정 — 무변경 권장]:** `/api/auth/me`는 `/api/auth/login`·`/api/auth/session` permitAll 매처보다 **덜 구체적이지 않다**. Spring Security 매처는 구체 경로가 선행하므로, `/api/auth/me`를 permitAll에 넣지 않으면 `/api/**` 광역 보호에 포함되어 401. **SecurityConfig 무변경**이 SC#1(401)을 충족한다. planner는 permitAll 매처가 `/api/auth/session`·`/api/auth/login`만 포함하고 `/api/auth/me`는 제외함을 테스트로 회귀 단언(lesson 05 매처 순서).

### ArchUnit (AUTH-11) — 현재 규칙이 이미 교차 컨텍스트 application 호출을 허용 → **무변경으로 GREEN, 단 의도 명시 검토** ([자율결정])
- **D-06:** **실측 결론: 현재 ArchUnit 규칙은 `auth/interfaces` → `identity/application`·`authorization/application` 호출을 이미 허용하며, AUTH-08/09 집계는 규칙 변경 없이 GREEN을 유지한다.**
  - **실측 근거:** `ArchitectureTest.hexagonalLayerDependencies()`는 **단일** `layeredArchitecture` 규칙으로, 레이어를 **접미 글로브**(`..interfaces..`, `..application..`, ...)로 **컨텍스트 무관 전역** 정의한다. 따라서 `auth/interfaces`가 `identity/application`을 호출하는 것은 단지 `Interfaces → Application`이며, `whereLayer("Interfaces").mayOnlyAccessLayers("Application","Domain")`로 **이미 허용**. 컨텍스트 경계(bounded context slice)를 구분하는 별도 ArchUnit 규칙은 **존재하지 않는다**(전 src/test 실측 — `ArchitectureTest` 1개 규칙뿐).
  - **선례:** `auth/application/IdentityLinkService`가 이미 `identity.application.IdentityApplicationService`를 import·호출하며 통과 중(Phase 3 lesson "auth.application→identity.application ownLayer 허용"의 실체). interfaces→타컨텍스트 application도 동일 메커니즘으로 통과.
  - **결정 [자율결정]:** **ArchUnit 규칙을 변경하지 않아도 GREEN.** AUTH-11 SC#3("교차 컨텍스트 의존이 명시적으로 허용된 규칙으로 통과")의 **GREEN 부분은 무변경으로 충족**된다.
    - **단, "명시성"의 갭(명시):** 현재 규칙은 컨텍스트 경계를 보지 않으므로 교차 컨텍스트 호출이 **"명시적으로 허용"이 아니라 "구분하지 않아 우연히 통과"**다. AUTH-11 문구의 의도가 "교차 컨텍스트 의존을 ArchUnit이 *인지·문서화*"라면, planner는 (a) **무변경 + me() 집계가 GREEN임을 단언하는 테스트 추가**(최소 — 본 결정 권장), 또는 (b) **컨텍스트 인지 규칙 추가**(예: `auth → identity/authorization application 허용`을 명시하는 `slices()`/추가 `ArchRule`)를 택할 수 있다.
    - **권장(simplicity-first + lesson 01 P1 "PLAN↔게이트 자기모순 회피"):** **(a) 무변경 + GREEN 단언.** 컨텍스트 슬라이스 규칙 신설은 baseline 전체(4 컨텍스트)에 영향을 주는 광역 변경이라 "내 정보 집계" 범위를 초과하고, 오탐/누수 위험(lesson 04 P1 — layered check가 third-party 누수를 못 잡음, 슬라이스 규칙도 사각 존재)이 있다. planner/사용자가 AUTH-11 "명시성" 요구 강도를 확인해 (a)/(b) 확정할 것.
  - **결정적 제약(공통):** 어느 쪽이든 `./gradlew test --tests *ArchitectureTest*`로 GREEN을 **실증**(lesson 03/04/05 — 게이트 정합은 주장이 아니라 테스트로). 신규 import(`auth.interfaces` → `identity.application`/`authorization.application`)가 게이트를 깨지 않음을 확인.
  - **third-party 누수 주의(lesson 04 P1 계승):** 신규 application read DTO는 **MyBatis/JPA 애너테이션을 application/interfaces에 노출하지 말 것**(`@Param`·`@Entity` 등). `EffectiveGrantDto`처럼 순수 DTO. direct 해석(D-02)은 JPA `findByUserId`만 쓰므로 MyBatis 무관 — `@Param` 누수 면이 없다.

### 검증 전략 (SC#1~3 — Testcontainers/WireMock/MockMvc, 라이브 구동 없음) ([자율결정])
- **D-07:** 전 과정을 `@SpringBootTest` + Testcontainers(postgres:16/redis:7) + WireMock(MockOidcServer) + MockMvc로 검증한다. 실 브라우저·실 IdP·라이브 포트(8080/5432/6379) 기동 금지(lesson 03/04/05 — 포트 혼잡).
  - **재사용:** `AbstractIntegrationTest` 상속 + Phase 5 `BffAuthSessionIT` 형판(`performWireMockLogin`, 세션 쿠키/CSRF 유틸, `identityService.invite` 시드). 신규 `BffAuthMeIT`(또는 `BffAuthSessionIT` 확장).
  - **SC별 단언 설계:**
    - **SC#1(신원, AUTH-08):** **실 User 시드 + 실 로그인.** `identityService.invite(email)` → `linkIdentity` (또는 WireMock authorization-code 흐름으로 신원 연결)로 실 User(id/email/status=ACTIVE) 생성 → 그 세션으로 `GET /api/auth/me` → `userId`=실 로컬 id, `email`=시드 email, `status`="ACTIVE" 단언. 비인증 `GET /api/auth/me` → **401** 단언(SC#1 후단).
    - **SC#2(권한, AUTH-09):** **실 Postgres 권한 시드.** 위 실 User에 `authorizationService.grantGlobalRole(userId, ADMIN)` · `grantMenuToUser(userId, menuId, EDITOR)` · `grantResourceToUser(userId, resourceId, VIEWER)`로 직접 부여 후 `GET /api/auth/me` 응답의 `roles`/`menus`/`resources`에 그 부여가 담기는지 단언. **권한 없는 사용자**는 빈 목록(`roles=[] 등`) 단언.
    - **SC#3(ArchUnit):** `./gradlew test --tests *ArchitectureTest*` GREEN. me() 집계 코드(auth.interfaces → 두 application import) 추가 후에도 통과함을 단언(D-06).
  - **oidcLogin() 우회 정책 [자율결정 — 결정적]:** **신원·권한 집계 단언(SC#1/SC#2)은 oidcLogin() 합성 claim으로 우회 금지**(lesson 05 "oidcLogin 우회 금지"). userId가 **실 로컬 User.id**여야 권한 시드(grant*는 그 userId로)와 응답이 정합한다 — 합성 claim(예: 42L)은 실 User/grant와 연결되지 않아 집계를 거짓 통과시킨다. **WireMock authorization-code 실 로그인으로 실 user_id를 확보**하거나, 시드한 User.id를 알고 그 id로 oidcLogin claim을 맞추되 **실 grant를 그 id로 영속**해야 한다. planner는 "principal user_id ↔ 시드 User.id ↔ grant userId" **3자 일치**를 보장하는 시드 전략을 명시할 것(가장 견고: WireMock 실 로그인 후 응답 userId를 읽어 그 id로 grant 시드 → 재요청).
  - **권한 데이터는 실 Postgres [자율결정]:** grant 영속·조회는 실 Postgres Testcontainers(H2 금지 — lesson 04). direct 해석(D-02)은 재귀 CTE 미사용이라 단순 `findByUserId` JPA 조회지만, 실 DB 라운드트립으로 단언한다.

### Claude's Discretion (planner/researcher 재량)
- Identity/Authorization read 메서드 시그니처·이름(`findUser`/`getUser`/`findPermissions` 등), read DTO 형태(record vs class)·필드명, `MeResponse` 중첩 구조(평탄 vs 버킷 객체), `me()` 메서드 시그니처, userId 타입 변환 방식(`Number.longValue` vs 캐스팅), readOnly 트랜잭션 적용 위치, 신규 IT 클래스 분리(`BffAuthMeIT`) vs `BffAuthSessionIT` 확장, 미존재 User의 HTTP 매핑(404 vs 500) — 모두 정본 표준·SC·ArchUnit 게이트·NFR 위반 없는 선에서 재량.
- **단, D-02(집계 범위)·D-06(ArchUnit 명시성 강도)는 재량이 아니라 planner/사용자 확인 사항.**

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 정본 표준 (단일 진실 공급원)
- `spring-backend-ddd-baseline.md` (문서 v1.0) — 프로젝트 전체 정본. Phase 6 핵심 절:
  - §3 **A-4 (인증/인가 분리)** — me()는 인증 신원(Identity) + 인가 권한(Authorization)을 *읽어* 한 응답에 결합. 분리 원칙을 깨지 않고 interfaces에서 *집계만* 한다.
  - §3 **A-5 (인가=포트/어댑터)** — 권한 조회는 application 경유(AuthorizationApplicationService/Port). interfaces가 도메인 repository를 직접 만지지 않는다.
  - §4.1 **불변 식별자 / soft reference** — userId(로컬 User.id)가 Identity↔Authorization 교차 참조의 매칭 키. grant.userId = User.id.
  - §5.1 **인증·세션은 과하게 DDD화하지 않는다** — me() 집계 표면은 얇은 interfaces 조율 + application read. 신규 도메인 모델 금지.

### 단계 정의 & 요구사항
- `.planning/ROADMAP.md` — Phase 6 정의, Goal, **Success Criteria 1~3**(신원 401분기, 권한 단일응답 결합, ArchUnit GREEN). Phase 5 경계(집계 없음).
- `.planning/REQUIREMENTS.md` — **AUTH-08 / AUTH-09 / AUTH-11**(Phase 6), Traceability, Out of Scope(권한 변경 API·프론트 제외).
- `.planning/PROJECT.md` — Constraints(스택·아키텍처 A-1~A-6), Key Decisions(방식 A·Flyway 불변성), "한 응답 결합은 의도적(SPA 라우팅/메뉴 가드 편의)" — D-03 근거.

### Phase 6 핵심 수정/조율 대상 (실측 — 반드시 정합)
- `src/main/java/com/anchors/baseline/auth/interfaces/AuthController.java` — **me() 추가 위치.** 현재 session()/login() + `SessionResponse` record + `@JsonInclude(NON_NULL)` + `OidcUser.getAttribute("user_id")` 패턴(D-03/D-04 형판). `/api/auth` RequestMapping.
- `src/main/java/com/anchors/baseline/identity/application/IdentityApplicationService.java` — **read 메서드 신설 대상(D-01).** 현재 invite/linkIdentity/disable 쓰기만, 클래스 레벨 `@Transactional`. read 0건.
- `src/main/java/com/anchors/baseline/identity/domain/model/User.java` — `@Getter`로 `getId()/getEmail()/getStatus()` 노출. `Email.getValue()`(VO), `UserStatus`(enum INVITED/ACTIVE/DISABLED). application이 평탄화해 DTO 반환.
- `src/main/java/com/anchors/baseline/identity/domain/repository/UserRepository.java` — `findById(Long)→Optional<User>` 존재(D-01 read가 조율).
- `src/main/java/com/anchors/baseline/authorization/application/AuthorizationApplicationService.java` — **read 메서드 신설 대상(D-02).** 현재 grant/revoke/create 쓰기만, read 0건. 6개 쓰기 포트 주입 중 — direct 권한 조회를 위해 GlobalRoleGrant/MenuGrant/ResourceGrant Repository.findByUserId 조율.
- `src/main/java/com/anchors/baseline/authorization/domain/repository/GlobalRoleGrantRepository.java` · `MenuGrantRepository.java` · `ResourceGrantRepository.java` — 각 `findByUserId(long)` **존재**(D-02 direct 해석의 조회 포트). MenuGrant/ResourceGrant는 `findByGroupId`도 존재(그룹 부여는 direct 해석에서 제외).
- `src/main/java/com/anchors/baseline/authorization/application/AuthorizationPort.java` · `PermissionReadPort.java` · `EffectiveGrantDto.java` — **참고(미사용 권장):** evaluate/listObjects/findEffectiveGrants는 리소스/액션 단위라 "사용자 전체 권한 열거"에 부적합(D-02 트레이드오프 근거). 신규 권한 DTO는 `EffectiveGrantDto`의 순수 평탄 DTO 패턴 계승.
- `src/main/java/com/anchors/baseline/auth/infrastructure/security/BaselineOidcUser.java` — `user_id`(`USER_ID_ATTRIBUTE`)로 로컬 User.id 병합. me() userId 출처(D-04). **interfaces에서 import 금지**(표준 OidcUser.getAttribute만).
- `src/main/java/com/anchors/baseline/auth/application/IdentityLinkService.java` — **교차 컨텍스트 호출 선례.** auth.application → identity.application import·호출이 ArchUnit 통과 중(D-06 근거). me()의 auth.interfaces → 두 application도 동일 메커니즘.

### ArchUnit 게이트 (AUTH-11 — 실측)
- `src/test/java/com/anchors/baseline/architecture/ArchitectureTest.java` — **유일한 계층 게이트.** `layeredArchitecture` 접미 글로브(`..interfaces../..application..` 컨텍스트 무관 전역) + `Interfaces mayOnlyAccessLayers(Application, Domain)`. **교차 컨텍스트 application 호출을 이미 허용 → 무변경 GREEN(D-06).** 컨텍스트 슬라이스 규칙은 부재(전 src/test 실측, 게이트 1개뿐).

### 검증 형판 (실측)
- `src/test/java/com/anchors/baseline/auth/BffAuthSessionIT.java` — **Phase 6 검증 형판.** `AbstractIntegrationTest` 상속, `@AutoConfigureMockMvc`, WireMock(`MockOidcServer`) authorization-code 실 흐름(`performWireMockLogin`), `oidcLogin()`(상태 단언용), `identityService.invite` 시드, 세션 쿠키/CSRF/`SessionRepository` read-back 유틸. me() IT가 이 인프라 확장.
- `src/test/java/com/anchors/baseline/auth/support/MockOidcServer.java` — WireMock OIDC discovery/token/jwks 스텁(`stubToken(oid,email,nonce)`). SC#1/#2 실 로그인에 재사용.
- `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` — 싱글턴 Testcontainers(postgres:16/redis:7). D-07 상속 대상.
- `src/test/java/com/anchors/baseline/auth/interfaces/MeController.java`(`/api/me`) 형판 — `OidcUser.getAttribute("user_id")` 표준 읽기 + infrastructure 미참조(D-04 형판). **Phase 6 미변경**(D-06 deferred).

### Lesson 교훈 (재발 방지)
- `.planning/lessons/05-2026-05-31.md` — **record + `@JsonInclude(NON_NULL)`로 Map.of NPE 회피(D-03), oidcLogin() 우회 금지·서버측 read-back(D-07), 환경 종속 값 하드코딩 금지(시드 email/oid는 `uniqueEmail`/`uniqueOid` 동적 생성), 입력검증 완전성**(me는 입력 거의 없으나 userId 타입 변환 안전성 — D-04).
- `.planning/lessons/04-2026-05-31.md` — **🔴 ArchUnit layered check가 third-party(ibatis/spring) 누수를 못 잡음(D-06 — application/interfaces DTO에 @Param/@Entity 금지), 투기적 VO 금지(D-02 direct 해석은 신규 추상 최소), types-first 웨이브, 실 Postgres Testcontainers(D-07).** Flyway 불변(Phase 6 스키마 변경 없음 — 무관하나 원칙 유지).
- `.planning/lessons/03-2026-05-31.md` — **RESEARCH의 "프레임워크 기본" 주장은 실 classpath/테스트로 검증**(D-06 ArchUnit 동작은 실측으로 확인됨), `auth.application→identity.application` ownLayer 허용 선례(D-06), Map.of null NPE → record(D-03).
- `.planning/phases/05-bff-auth-session/05-CONTEXT.md` — Phase 5 D-01~D-07. AuthController 신설·`/api/me` 공존·permitAll 매처·ArchUnit 무변경 결정. Phase 6가 그 위에 me()를 얹는다.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AuthController`(auth/interfaces)** — Phase 5 신설. me() 메서드를 같은 컨트롤러에 추가(경로 prefix `/api/auth` 일관). `SessionResponse` record + `@JsonInclude` + `OidcUser.getAttribute("user_id")` 패턴 그대로 계승.
- **`UserRepository.findById` + `User`/`Email`/`UserStatus` getter** — Identity read 메서드가 조율. application이 평탄 DTO로 캡슐화.
- **`GlobalRoleGrant/MenuGrant/ResourceGrant Repository.findByUserId`** — Authorization direct 권한 조회 포트가 **이미 존재**. 신규 application read가 3개를 조율(신규 CTE/쿼리 불필요 — D-02 direct 해석의 핵심 단순화).
- **`IdentityLinkService`(auth.application→identity.application)** — 교차 컨텍스트 호출이 ArchUnit 통과하는 실증 선례. me() 집계가 동일 메커니즘으로 안전.
- **`BffAuthSessionIT` + `MockOidcServer` + `AbstractIntegrationTest`** — WireMock 실 로그인·실 User 시드(`identityService.invite`)·세션 유틸 완비. me() IT가 grant 시드만 추가해 확장.
- **`@Transactional` application 서비스 + `EffectiveGrantDto` 순수 DTO 패턴** — read 메서드 readOnly 트랜잭션·평탄 DTO 형판.

### Established Patterns
- **컨텍스트 = `{context}/{domain,application,infrastructure,interfaces}`** — 신규 read 메서드는 각 컨텍스트 application, me()는 auth/interfaces, 응답 DTO는 interfaces. 도메인 빈약(§5.1).
- **interfaces는 표준 Spring Security 타입만 의존, infrastructure 구현 타입 미참조** — ArchUnit `Interfaces → {Application, Domain}` 정합(MeController/AuthController 형판). me()는 추가로 `identity.application`·`authorization.application` 의존(전역 레이어 글로브상 `Interfaces→Application` 허용 — D-06).
- **application read DTO로 도메인 엔티티 누수 차단** — `User`/`RoleName`/grant 엔티티를 interfaces로 노출하지 않고 application이 평탄 DTO 반환(Phase 4 `EffectiveGrantDto` 패턴, CQRS-lite).
- **types-first 선형 wave**(read DTO·메서드 → me() → IT) — 전방참조 컴파일 위험 제거(lesson 03/04/05).
- **SC 충실도: oidcLogin() 우회 금지 + 서버측 실 데이터 단언** — me() 집계는 실 User+실 grant가 응답에 담기는지 통합 단언(D-07).

### Integration Points
- **auth/interfaces → identity/application + authorization/application** — me()의 핵심 신규 의존(교차 컨텍스트 2개). ArchUnit 전역 글로브상 `Interfaces→Application`으로 통과(D-06). 신규 import가 게이트 GREEN 유지함을 테스트로 단언.
- **principal user_id ↔ 시드 User.id ↔ grant userId 3자 일치** — me() 집계 검증의 결정적 정합점(D-07). WireMock 실 로그인으로 실 user_id 확보 → 그 id로 grant 시드 → 재요청이 가장 견고.
- **`/api/auth/me`는 `/api/**` 보호 매처에 포함(permitAll 아님)** — 비인증 401이 SecurityConfig 무변경으로 자연 충족(D-05). permitAll 매처가 me를 제외함을 회귀 단언.
- **신원·권한 단일 응답 결합** — interfaces가 두 application read DTO를 받아 하나의 `MeResponse`로 조립(D-03). SPA 메뉴/라우트 가드 소비 계약.

</code_context>

<specifics>
## Specific Ideas

- **AUTH-08/09의 핵심은 "한 번의 호출로 SPA가 라우팅/메뉴 가드에 필요한 신원+권한을 모두 확보"** — PROJECT.md가 "한 응답 결합은 의도적"이라 명시. me()는 SPA가 부트스트랩 시 1회 호출하는 표준 표면.
- **§5.1 설계 철학 계승** — Phase 6 산출물의 본질은 "AuthController.me() 1개 + 두 application read 메서드 + read DTO"다. 신규 도메인/애그리거트/VO 금지(lesson 04 투기적 VO 안티패턴).
- **direct 권한 해석은 baseline 최소 표면** — 복제 업무 프로젝트가 그룹/계층 상속 집계가 필요하면 그때 확장. baseline은 "내 직접 권한 읽기"의 명료한 골격을 제공(D-02 트레이드오프).
- **골격 재사용성(Core Value)** — `/api/auth/me`는 후속 복제 프로젝트가 IdP/권한 데이터만 바꿔 그대로 쓰는 표준 "내 정보" 표면. 신원+권한 결합 응답 계약이 명료히 드러나야 함.

</specifics>

<deferred>
## Deferred Ideas

- **그룹 상속 + 리소스 계층 상속(재귀 CTE)을 me() 권한에 포함** — D-02가 direct로 고정. SPA가 상속 권한까지 필요로 하면 (a) GroupMember 합산 또는 (b) 신규 "user 전체 effective grants" CTE 읽기 모델로 확장. planner/사용자가 AUTH-09 해석을 확인 후 결정.
- **컨텍스트 인지 ArchUnit 슬라이스 규칙**(`auth → identity/authorization application` 명시 허용을 슬라이스로 표현) — D-06이 무변경 GREEN으로 고정. AUTH-11 "명시성" 강도에 따라 planner가 신설 검토(baseline 광역 영향 — 신중).
- **`/api/me`(MeController) 정리/deprecate** — Phase 5 D-01에서 Phase 6로 이연. me()가 신원을 반환하므로 `/api/me`(레거시 SC#2 프로브)와 부분 중복. 단 `/api/me`는 Phase 3/5 테스트(`BffAuthIT`/`BffAuthSessionIT`)가 401/200 프로브로 사용 중 → **제거 시 회귀**. 본 phase는 **공존 유지 권장**(제거는 별도 정리 작업). [자율결정 — 신뢰도 중간]
- **인가 권한 변경 REST API(grant/revoke 노출)** — REQUIREMENTS Out of Scope(복제 업무 프로젝트 영역). v1.1 범위 밖.
- **권한 응답 캐싱·ETag, 권한 변경 시 무효화** — 성능/일관성 최적화. baseline은 매 호출 DB 조회(단순). 후속 최적화 후보.

</deferred>

---

## Open Questions / Decisions (자율 결정 감사 로그)

> 비대화형 세션에서 AskUserQuestion 대신 자율 결정한 항목. planner/사용자가 감사할 것.
> **D-02·D-06은 신뢰도 [중간] — 최우선 감사 대상.**

| # | 결정 | 선택 | 근거 | 신뢰도 |
|---|------|------|------|--------|
| D-01 | Identity 신원 조회 | `IdentityApplicationService`에 신규 read 메서드 + 읽기 DTO 반환(User 엔티티 미노출) | read 메서드 부재 실측. 헥사고날·CQRS-lite 정합(EffectiveGrantDto 패턴). User는 @Getter 노출 | [높음] |
| D-02 | **AUTH-09 권한 집계 범위** | **직접 부여(direct)만** — GlobalRole/Menu/Resource `findByUserId` 3종 조율. 그룹/계층 상속 제외 | 검증된 finder 재사용, 신규 CTE 불필요, ROADMAP 3버킷 명시 정합, §5.1 과집계 금지. **단 SPA가 상속 권한 필요 시 부족** | **[중간]** |
| D-03 | me() 응답 DTO | 단일 record `MeResponse`(신원+권한 결합), 빈 컬렉션은 `[]`, nullable 스칼라만 NON_NULL | ROADMAP 단일응답·PROJECT 의도적 결합. Phase 5 record+@JsonInclude 계승(Map.of NPE 회피) | [높음] |
| D-04 | userId 출처 | `OidcUser.getAttribute("user_id")` 표준 읽기, infrastructure 타입 미참조 | A-6 로컬 PK 병합. MeController/session() 동일 출처. ArchUnit interfaces→infra 자연 통과 | [높음] |
| D-05 | `/api/auth/me` 보안 | permitAll 제외 → `/api/**` 보호로 비인증 401 자연 충족. SecurityConfig 무변경 | SC#1 "비인증 401" 직접. session(permitAll 200)과 의도적 반대. surgical | [높음] |
| D-06 | **ArchUnit (AUTH-11)** | **무변경으로 GREEN** — 현재 전역 레이어 글로브가 교차 컨텍스트 application 호출 이미 허용. (권장: 무변경+GREEN 단언 테스트) | 게이트 1개 실측(컨텍스트 슬라이스 부재). IdentityLinkService 선례. 슬라이스 신설은 baseline 광역 영향. **단 "명시성" 갭 존재** | **[중간]** |
| D-07 | 검증 전략 | Testcontainers+WireMock+MockMvc, **oidcLogin 우회 금지**, 실 User+실 grant 시드, principal↔User.id↔grant.userId 3자 일치 | 라이브 포트 혼잡. lesson 05 우회 금지. 합성 claim은 집계 거짓통과 위험. 실 Postgres(lesson 04) | [높음] |
| D-08 | phase dir slug | placeholder `06-new-phase`(빈 디렉터리) 제거, `06-me-aggregation` 신설 | placeholder 빈 디렉터리였음. Phase 명 "내 정보 신원·권한 집계" | [중간] |

*Phase: 6-me-aggregation*
*Context gathered: 2026-05-31*
