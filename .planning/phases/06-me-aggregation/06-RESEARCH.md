# Phase 6: 내 정보 신원·권한 집계 - Research

**Researched:** 2026-05-31
**Domain:** Spring Boot MVC + 헥사고날 DDD — auth/interfaces 교차 컨텍스트 집계 (Identity + Authorization read)
**Confidence:** HIGH (전 핵심 주장을 소스 코드 실측으로 확정)

---

<user_constraints>
## User Constraints (CONTEXT.md 기반)

### Locked Decisions

- **D-01:** `IdentityApplicationService`에 신규 read 메서드 신설 + 읽기 DTO 반환. `User` 엔티티 미노출.
- **D-02:** 권한 집계 범위 = **직접 부여(direct)** — `GlobalRoleGrantRepository.findByUserId` / `MenuGrantRepository.findByUserId` / `ResourceGrantRepository.findByUserId` 3종 조율. 그룹·계층 상속 제외. **(아래 D-02 권고 섹션에서 코드 실측 근거로 최종 권고 A/B 확정)**
- **D-03:** `MeResponse` 단일 record (신원+권한 결합). 빈 컬렉션 `[]`, nullable 스칼라만 `@JsonInclude(NON_NULL)`. Phase 5 `SessionResponse` 패턴 계승.
- **D-04:** userId 출처 = `OidcUser.getAttribute("user_id")`. infrastructure 타입(`BaselineOidcUser`) import/cast 금지.
- **D-05:** `/api/auth/me`는 permitAll 제외 → `/api/**` 보호 매처로 비인증 401. SecurityConfig 무변경.
- **D-06:** ArchUnit 무변경으로 GREEN. (a) 무변경 + GREEN 단언 테스트 추가 권장.
- **D-07:** Testcontainers + WireMock + MockMvc. oidcLogin() 우회 금지. 실 User + 실 grant 시드. principal user_id ↔ User.id ↔ grant.userId 3자 일치.

### Claude's Discretion

- read 메서드 시그니처·이름, read DTO 형태(record vs class)·필드명, `MeResponse` 중첩 구조, `me()` 메서드 시그니처, userId 타입 변환 방식, readOnly 트랜잭션 적용 위치, IT 클래스 분리 여부, 미존재 User HTTP 매핑(404 vs 500).

### Deferred Ideas (OUT OF SCOPE)

- 그룹 상속 + 리소스 계층 상속(재귀 CTE)을 me() 권한에 포함
- 컨텍스트 인지 ArchUnit 슬라이스 규칙 신설
- `/api/me`(MeController) 정리/deprecate
- 인가 권한 변경 REST API (grant/revoke 노출)
- 권한 응답 캐싱·ETag, 권한 변경 시 무효화

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | 설명 | Research 지원 |
|----|------|---------------|
| AUTH-08 | `GET /api/auth/me` → userId·email·status (Identity application 조회). 비인증 시 401 | `IdentityApplicationService`에 `findById` 기반 read 메서드 신설 + `UserRepository.findById` 재사용 |
| AUTH-09 | 동일 응답에 전역 역할·메뉴·리소스 권한(Authorization application 조회) 단일 결합 | `GlobalRoleGrantRepository.findByUserId` / `MenuGrantRepository.findByUserId` / `ResourceGrantRepository.findByUserId` 3종 직접 재사용 가능 — 실측 확인 |
| AUTH-11 | ArchUnit 계층 의존 테스트 GREEN 유지. auth/interfaces → Identity·Authorization application 교차 호출 허용 | 현재 `layeredArchitecture` 전역 글로브가 이미 허용 — 무변경 GREEN 실측 확인 |

</phase_requirements>

---

## Summary

Phase 6는 `AuthController`(auth/interfaces)에 `me()` 메서드 하나를 추가하고, `IdentityApplicationService`와 `AuthorizationApplicationService`에 각각 read 메서드를 신설하며, `MeResponse` record로 결합해 반환하는 것이 전부다. 신규 도메인/애그리거트/VO가 없다.

**D-02 핵심 결론 (코드 실측):** direct 권한 집계는 **(A) 기존 JPA 파생 쿼리 3종 재사용으로 즉시 가능**하다. 반면 effective(그룹·계층 상속 포함) 열거는 기존 `PermissionReadPort.findEffectiveGrants`가 `(userId, resourceId)` 단위라 "사용자 전체 권한 열거"에 **직접 재사용 불가** — `resource_id`를 전체 스캔하는 신규 CTE가 필요해 이 phase 범위를 초과한다. **권고: (A) direct 채택 + 응답 형태를 확장 가능하게 설계.**

**D-06 핵심 결론 (코드 실측):** `ArchitectureTest.hexagonalLayerDependencies()`는 컨텍스트 무관 접미 글로브(`..interfaces..`, `..application..`)로 정의된 단일 규칙이다. `auth/interfaces` → `identity/application`·`authorization/application` 호출은 단지 "Interfaces → Application" 계층 이동이며 이미 `mayOnlyAccessLayers("Application","Domain")`으로 허용된다. `IdentityLinkService`(`auth.application` → `identity.application`)가 동일 메커니즘으로 이미 통과 중이라는 실증 선례가 있다. **무변경 GREEN.**

**Primary recommendation:** types-first 선형 Wave(read DTO·메서드 → me() → IT)로 3플랜 구성. Wave 1은 read DTO + application 메서드, Wave 2는 `AuthController.me()`, Wave 3은 `BffAuthMeIT`.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| GET /api/auth/me 응답 조립 | auth/interfaces (AuthController) | — | SPA 소비 계약. 얇은 조율 표면(§5.1) |
| 신원 조회 (userId→email/status) | identity/application | identity/domain | 도메인 포트 `UserRepository.findById` 경유 |
| 권한 열거 (direct grants) | authorization/application | authorization/domain | 도메인 포트 3종 `findByUserId` 경유. infrastructure JPA 파생 쿼리 |
| 보안 매처 (401 강제) | auth/infrastructure/security | — | SecurityConfig 기존 `/api/**` 보호 매처 — 무변경 |
| ArchUnit 계층 게이트 | test/architecture | — | 단일 규칙 GREEN — 무변경 |

---

## D-02 권한 집계 범위 — 코드 실측 권고 (A vs B)

이 섹션은 CONTEXT.md D-02의 최우선 감사 요청에 대한 **연구자 최종 권고**다. 코드 실측 근거로 명확히 판정한다.

### 실측 사실

**기존 인프라:**

1. `PermissionReadPort.findEffectiveGrants(userId, resourceId)` — **리소스 단위** 판정. userId + **특정 resourceId**를 받아 그 리소스에 대한 effective grants(직접·그룹·계층 상속 합산)를 반환한다. "사용자의 전체 권한 열거"에 **직접 재사용 불가** — resource_id가 필수 파라미터라 전체 스캔이 안 된다.

2. `PermissionReadPort.listAccessibleResourceIds(userId, action)` — **액션 단위** 조회. userId + 특정 action으로 접근 가능한 resource_id 목록 반환. 전역 역할·메뉴 권한은 **전혀 다루지 않음**.

3. `GlobalRoleGrantRepository.findByUserId(long)` — userId에 직접 부여된 전역 역할 목록. **즉시 재사용 가능**. JPA 파생 쿼리(`GlobalRoleGrantJpaRepository extends JpaRepository<GlobalRoleGrant,Long>, GlobalRoleGrantRepository`). 반환: `List<GlobalRoleGrant>` (각 `userId`, `role: RoleName`, `createdAt`).

4. `MenuGrantRepository.findByUserId(long)` — userId에 직접 부여된 메뉴 권한 목록. **즉시 재사용 가능**. 반환: `List<MenuGrant>` (각 `userId`, `menuId`, `role: RoleName`). 그룹 부여(`findByGroupId`)는 별도 메서드 — direct 해석에서 제외.

5. `ResourceGrantRepository.findByUserId(long)` — userId에 직접 부여된 리소스 권한 목록. **즉시 재사용 가능**. 반환: `List<ResourceGrant>` (각 `userId`, `resourceId`, `role: RoleName`). 그룹 부여(`findByGroupId`)는 별도 메서드 — direct 해석에서 제외.

**effective 열거에 필요한 신규 작업 분석:**

effective(상속 포함) 열거를 구현하려면:
- **전역 역할:** 그룹 멤버십 상속 없음 — direct = effective. 차이 없음.
- **메뉴 권한 effective:** `GroupMemberRepository.findByUserId(userId)` → 소속 그룹 목록 → `MenuGrantRepository.findByGroupId(groupId)` 루프 (또는 JOIN 쿼리 신설). **중간 복잡도** — 신규 MyBatis/JPA 쿼리 불필요, Java 애플리케이션 레벨 합산 가능.
- **리소스 권한 effective:** 그룹 멤버십 합산 + **계층 상속 (재귀 CTE)**. 현재 `findEffectiveGrants`는 (userId, 특정 resourceId) → 한 리소스의 effective grants. "모든 리소스에 대한 effective grants"를 열거하려면 전체 resource_id를 먼저 구한 뒤 CTE를 돌리거나, "userId가 접근 가능한 리소스 전체 + 각 역할"을 반환하는 신규 CTE를 작성해야 한다. **고복잡도** — 신규 MyBatis XML SQL + 새 DTO 설계 필요. lesson 04 "재귀 CTE는 실 Postgres Testcontainers로만 검증" 원칙까지 적용.

### 권고: **(A) direct 채택 — [높음]**

**결론:** **(A) direct 기반 권장**. 이유:

1. **기존 3종 JPA 파생 쿼리로 즉시 구현 가능** — 신규 SQL/MyBatis/CTE 없음. Wave 1 작업이 단순하다.
2. **effective 리소스 열거는 이 phase 범위 초과** — 신규 CTE 읽기 모델 + 실 Postgres 검증이 필요하며 "내 정보 집계" 범위를 과도하게 늘린다(§5.1 과집계 금지).
3. **정합성 footgun 반론은 불완전하다** — `PermissionEvaluator.evaluate()`(서버 판정)는 *특정 리소스/액션*에 대한 boolean 판정이다. me() 권한은 "내가 부여받은 권한 목록" — 두 표면은 용도가 다르다(판정 vs 열거). SPA 메뉴/라우트 가드가 "내 직접 부여 목록"을 받아 UI를 렌더링하고, 서버는 각 요청에서 effective evaluate를 수행한다 — 이는 표준 패턴이다.
4. **전역 역할은 direct = effective** — `GlobalRoleGrant`는 그룹 상속 없음. 불일치가 없다.
5. **메뉴/리소스 그룹 상속 gap:** SPA가 그룹으로 부여된 메뉴/리소스를 UI에서 표시해야 한다면 direct-only로는 부족하다. 이 경우 **(A-확장)** 경로가 있다 — `GroupMemberRepository.findByUserId` + `MenuGrantRepository.findByGroupId` 합산은 신규 CTE 없이 Java 레벨 합산으로 가능하며 중간 복잡도다. **planner가 SC#2("권한 전체") 해석을 "직접 부여"로 확정 vs "그룹 상속 포함"으로 확장 여부를 결정할 것.**

**응답 형태는 확장 가능하게 설계할 것:** direct-only로 시작하더라도 `roles`/`menus`/`resources` 3버킷 구조는 그대로 유지하고, 향후 그룹/계층 상속을 추가할 때 API 계약(필드 구조)이 바뀌지 않도록 한다.

**(B) effective 열거가 필요한 조건:** SPA가 bootstrap 시 "서버 판정과 동일한" 권한 목록을 UI 캐시로 받아야 하는 요구가 확인된 경우. 이 경우 planner는 별도 phase(또는 이 phase 범위 확대)로 신규 CTE 읽기 모델을 신설해야 한다.

---

## Standard Stack

### Core (신규 코드 없이 재사용)

| 구성요소 | 버전/경로 | 용도 | 재사용 방법 |
|----------|-----------|------|-------------|
| `GlobalRoleGrantRepository.findByUserId` | domain/repository | 전역 역할 직접 부여 조회 | `AuthorizationApplicationService` 신규 read 메서드에서 호출 |
| `MenuGrantRepository.findByUserId` | domain/repository | 메뉴 직접 부여 조회 | 동상 |
| `ResourceGrantRepository.findByUserId` | domain/repository | 리소스 직접 부여 조회 | 동상 |
| `UserRepository.findById(Long)` | identity/domain/repository | 신원 조회 | `IdentityApplicationService` 신규 read 메서드에서 호출 |
| `AuthController` | auth/interfaces | me() 메서드 추가 대상 | `/api/auth` RequestMapping 기존 컨트롤러 확장 |
| `BffAuthSessionIT` | test/auth | me() 통합 테스트 형판 | `AbstractIntegrationTest` + WireMock + MockMvc 인프라 재사용 |

### 신규 산출물 (최소)

| 산출물 | 위치 | 설명 |
|--------|------|------|
| `UserView` record | identity/application | `(Long id, String email, String status)` — application read DTO |
| `PermissionView` record | authorization/application | `(List<String> roles, List<MenuView>, List<ResourceView>)` — application read DTO |
| `findUser(long userId)` 메서드 | `IdentityApplicationService` | `@Transactional(readOnly=true)` 오버라이드 |
| `findPermissions(long userId)` 메서드 | `AuthorizationApplicationService` | `@Transactional(readOnly=true)` 오버라이드 |
| `MeResponse` record | auth/interfaces | 신원+권한 결합 응답 계약 |
| `AuthController.me()` | auth/interfaces | `GET /api/auth/me` 핸들러 |
| `BffAuthMeIT` 클래스 | test/auth | SC#1/2/3 통합 단언 |

### Package Legitimacy Audit

> 신규 외부 패키지 없음 — 기존 Spring Boot, Spring Security, Spring Data JPA, Lombok, ArchUnit 재사용. 신규 의존성 추가 없으므로 slopcheck 대상 없음.

**신규 설치 패키지:** 없음

---

## Architecture Patterns

### 데이터 흐름 요약

```
GET /api/auth/me (인증)
        ↓
AuthController.me(OidcUser principal)
        ↓ getAttribute("user_id") → Long userId
        ├─── IdentityApplicationService.findUser(userId)
        │         └─ UserRepository.findById(userId) → User
        │                   └─ → UserView(id, email, status)
        │
        └─── AuthorizationApplicationService.findPermissions(userId)
                  ├─ GlobalRoleGrantRepository.findByUserId(userId) → List<GlobalRoleGrant>
                  ├─ MenuGrantRepository.findByUserId(userId)       → List<MenuGrant>
                  └─ ResourceGrantRepository.findByUserId(userId)   → List<ResourceGrant>
                            └─ → PermissionView(roles, menus, resources)
                                      ↓
                        MeResponse(UserView + PermissionView)
                                      ↓
                               200 OK (JSON)

GET /api/auth/me (비인증)
        ↓
SecurityConfig /api/** 보호 매처
        ↓
401 Unauthorized
```

### 권장 패키지 구조

```
auth/interfaces/
└── AuthController.java          # me() 추가 (기존 파일 수정)
    └── MeResponse.java          # 내부 record (또는 별도 파일)

identity/application/
└── IdentityApplicationService.java  # findUser(long) 메서드 추가
    └── UserView.java                # 신규 read DTO record

authorization/application/
└── AuthorizationApplicationService.java  # findPermissions(long) 메서드 추가
    └── PermissionView.java               # 신규 read DTO record (중첩 MenuView/ResourceView 포함)

test/auth/
└── BffAuthMeIT.java    # 신규 통합 테스트 (또는 BffAuthSessionIT 확장)
```

### Pattern 1: application read 메서드 — readOnly 트랜잭션 오버라이드

기존 application 서비스는 클래스 레벨 `@Transactional`(쓰기)이다. read 메서드는 메서드 레벨로 오버라이드한다.

```java
// [VERIFIED: 코드 실측] IdentityApplicationService 패턴
@Service
@RequiredArgsConstructor
@Transactional   // 클래스 레벨 = 쓰기 기본값
public class IdentityApplicationService {

    // 신규 read 메서드 — 메서드 레벨 readOnly 오버라이드
    @Transactional(readOnly = true)
    public UserView findUser(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        return new UserView(user.getId(), user.getEmail().getValue(), user.getStatus().name());
    }
}
```

### Pattern 2: me() — infrastructure 타입 미참조, 표준 OidcUser API만

```java
// [VERIFIED: AuthController.session() 실측] 계승 패턴
@GetMapping("/me")
public MeResponse me(@AuthenticationPrincipal OidcUser principal) {
    // infrastructure BaselineOidcUser 미참조 — ArchUnit interfaces→infrastructure 자연 통과
    Object rawId = principal.getAttribute("user_id");
    long userId = ((Number) rawId).longValue();  // Phase 5 "42L = Long" 실측

    UserView identity = identityService.findUser(userId);
    PermissionView permissions = authorizationService.findPermissions(userId);
    return new MeResponse(identity, permissions);
}
```

### Pattern 3: read DTO — record + @JsonInclude (Phase 5 SessionResponse 계승)

```java
// [VERIFIED: AuthController.SessionResponse 실측] 계승 패턴
// auth/interfaces에 위치 — 응답 계약은 interfaces 소유
public record MeResponse(
        Long userId,
        String email,
        String status,
        List<String> roles,
        List<MenuEntry> menus,
        List<ResourceEntry> resources) {

    // 중첩 record 예시
    public record MenuEntry(long menuId, String role) {}
    public record ResourceEntry(long resourceId, String role) {}
}
```

빈 컬렉션은 `[]`으로 직렬화 — null 아님. nullable 스칼라만 `@JsonInclude(NON_NULL)` 선택 적용.

### Anti-Patterns

- **interfaces가 도메인 엔티티(User, GlobalRoleGrant 등)를 직접 받는 것** — application read DTO(`UserView`, `PermissionView`)로 캡슐화해야 한다. JPA 엔티티 누수 + ArchUnit Domain→Interfaces 위반.
- **infrastructure 타입(`BaselineOidcUser`) cast** — ArchUnit interfaces→infrastructure 위반. `OidcUser.getAttribute("user_id")`만 사용.
- **application 포트에 `@Param` 등 MyBatis 애너테이션 노출** — lesson 04 P1. direct 해석은 JPA 파생 쿼리만 사용하므로 이 위험이 없다(MyBatis 비사용 경로).
- **투기적 VO 신설** — lesson 04 P2. direct 3종 `findByUserId`는 기존 도메인 엔티티를 그대로 쓴다. 신규 VO 불필요.
- **oidcLogin() 합성 claim으로 me() 집계 단언** — lesson 05. userId claim이 실 User.id와 연결되지 않으면 grant 시드와 응답이 불일치해 집계가 거짓 통과한다.

---

## Don't Hand-Roll

| 문제 | 직접 구현 금지 | 재사용 | 이유 |
|------|--------------|--------|------|
| userId로 User 조회 | 신규 쿼리 작성 | `UserRepository.findById(Long)` | 이미 identity/domain/repository에 존재, JPA 구현 완비 |
| userId로 전역 역할 조회 | 직접 SQL | `GlobalRoleGrantRepository.findByUserId` | JPA 파생 쿼리로 이미 구현됨 |
| userId로 메뉴 권한 조회 | 직접 SQL | `MenuGrantRepository.findByUserId` | 동상 |
| userId로 리소스 권한 조회 | 직접 SQL | `ResourceGrantRepository.findByUserId` | 동상 |
| 비인증 401 | SecurityConfig 변경 | 기존 `/api/**` 보호 매처 | D-05 — 무변경으로 자연 충족 |
| 교차 컨텍스트 테스트 인프라 | 신규 IT 인프라 | `BffAuthSessionIT` + `MockOidcServer` + `AbstractIntegrationTest` | WireMock 실 로그인 인프라 완비 |

---

## Common Pitfalls

### Pitfall 1: oidcLogin() 합성 claim으로 집계 단언

**무엇이 잘못되는가:** `oidcLogin().userInfoToken(u -> u.claim("user_id", 42L))`로 세션을 만들면 userId=42가 되지만, DB에는 id=42인 User가 없거나 42로 grant를 심지 않으면 집계가 빈 응답을 반환한다. 빈 응답도 200을 내므로 테스트가 GREEN으로 거짓 통과한다.

**왜 발생하는가:** 합성 claim은 Spring Security 레이어 단언에는 충분하지만 DB 라운드트립이 필요한 집계 단언에는 부족하다.

**회피법:** WireMock 실 로그인(`performWireMockLogin`) → 응답 userId 확인 → 그 userId로 grant 시드 → 재요청 → 집계 단언. 또는 `identityService.invite(email)` 반환 userId를 oidcLogin claim과 일치시키고 **그 userId로** grant를 심은 뒤 `mvc.perform(get("/api/auth/me").with(oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))))`로 단언.

**경고 신호:** 권한 시드 후 me() 호출 시 `roles`/`menus`/`resources`가 항상 `[]`인데 200 반환.

### Pitfall 2: 클래스 레벨 @Transactional이 read 메서드에도 적용됨

**무엇이 잘못되는가:** `AuthorizationApplicationService`와 `IdentityApplicationService` 모두 클래스 레벨 `@Transactional`(기본 = 읽기·쓰기)이다. readOnly 없이 read 메서드를 추가하면 읽기 전용 커넥션 최적화가 사라진다.

**회피법:** read 메서드에 `@Transactional(readOnly = true)` 명시적 오버라이드.

### Pitfall 3: application read DTO에 도메인 엔티티 직접 노출

**무엇이 잘못되는가:** `findUser()`가 `User` 엔티티를 반환하면 `auth/interfaces`가 `identity/domain/model/User`를 import해야 한다 → ArchUnit `Interfaces.mayOnlyAccessLayers(Application, Domain)` 상으로는 통과하나, JPA 엔티티(`@Entity`, `@Embedded` 등) 노출이 interfaces 계층에 영속 기술을 누수시킨다.

**회피법:** application read 메서드가 평탄 record DTO(`UserView`, `PermissionView`)를 반환하고, interfaces는 그 DTO만 받는다. `EffectiveGrantDto` 패턴(Phase 4) 계승.

### Pitfall 4: SecurityConfig에 `/api/auth/me` permitAll 추가

**무엇이 잘못되는가:** 신원/권한을 노출하는 엔드포인트를 permitAll에 넣으면 비인증 접근 시 `principal == null` → `findUser(null)` → NPE 또는 `IllegalArgumentException`. SC#1 "비인증 시 401" 요구 위반.

**회피법:** D-05 — SecurityConfig 무변경. `/api/**` 보호 매처에 자연히 포함. `permitAllMatchers()` 테스트로 `/api/auth/me`가 허용 목록에 없음을 회귀 단언.

### Pitfall 5: principal.getAttribute("user_id") 타입 캐스팅

**무엇이 잘못되는가:** `getAttribute("user_id")`는 `Object` 반환. `(Long) rawId`로 직접 캐스팅하면 `Integer`로 들어온 경우(일부 IdP가 number를 int로 직렬화) `ClassCastException`.

**회피법:** `((Number) rawId).longValue()` — Phase 5 `BffAuthSessionIT.sessionAuthenticatedReturnsUserId`에서 `claim("user_id", 42L)` → `Long` 확인. `Number` 상위 캐스팅이 더 안전하다.

### Pitfall 6: 그룹 부여(findByGroupId)를 direct 해석에서 실수로 포함

**무엇이 잘못되는가:** `MenuGrantRepository.findByGroupId`와 `ResourceGrantRepository.findByGroupId`가 존재한다. `findByUserId` 대신 `findByGroupId`까지 포함하면 direct 해석이 깨지고, 그룹 membership 조회 없이 의미 없는 groupId를 userId로 쿼리하는 버그가 된다.

**회피법:** direct read 메서드는 `findByUserId(long userId)`만 호출한다. `findByGroupId`는 호출하지 않는다.

---

## Validation Architecture

> `nyquist_validation: true` (config.json 확인) — 이 섹션 필수.

### Test Framework

| 속성 | 값 |
|------|----|
| Framework | JUnit 5 + Spring Boot Test (`@SpringBootTest(RANDOM_PORT)`) |
| Config | `AbstractIntegrationTest` (싱글턴 Testcontainers postgres:16 + redis:7) |
| Quick run | `./gradlew test --tests *BffAuthMeIT* -x` |
| Full suite | `./gradlew test --rerun-tasks` |
| ArchUnit | `./gradlew test --tests *ArchitectureTest*` |

### Phase Requirements → Test Map

| Req ID | 행위 | Test Type | 자동화 명령 | 파일 존재 여부 |
|--------|------|-----------|-------------|----------------|
| AUTH-08-a | 인증 상태 `GET /api/auth/me` → 200 + userId·email·status | integration | `./gradlew test --tests *BffAuthMeIT*.meReturnsIdentity` | ❌ Wave 0 |
| AUTH-08-b | 비인증 `GET /api/auth/me` → 401 | integration | `./gradlew test --tests *BffAuthMeIT*.meUnauthenticatedReturns401` | ❌ Wave 0 |
| AUTH-09-a | 인증 + grant 시드 → me() 응답에 roles/menus/resources 포함 | integration | `./gradlew test --tests *BffAuthMeIT*.meReturnsGrantedPermissions` | ❌ Wave 0 |
| AUTH-09-b | 권한 없는 사용자 → roles=[] menus=[] resources=[] | integration | `./gradlew test --tests *BffAuthMeIT*.meReturnsEmptyPermissionsForNewUser` | ❌ Wave 0 |
| AUTH-11 | ArchUnit GREEN — me() 신규 import 후에도 통과 | architecture | `./gradlew test --tests *ArchitectureTest*` | ✅ 기존 존재 |

### 핵심 단언 전략: 3자 일치

모든 AUTH-08/09 통합 테스트는 다음 3자 일치를 보장해야 한다.

```
principal.getAttribute("user_id") == seededUser.id == grant.userId
```

**권장 시드 전략 (가장 견고):**

```
Option A (WireMock 실 로그인):
1. identityService.invite(email) → seededUserId
2. performWireMockLogin(oid, email) → 세션 쿠키 + userId 확인
3. authorizationService.grantGlobalRole(seededUserId, ADMIN) (트랜잭션 시드)
4. GET /api/auth/me (세션 쿠키) → 응답 단언

Option B (oidcLogin 클레임 일치):
1. identityService.invite(email) → seededUserId (= Long)
2. authorizationService.grantGlobalRole(seededUserId, ADMIN)
3. mvc.perform(get("/api/auth/me")
       .with(oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))))
   → 단언
(oidcLogin 우회지만 seededUserId와 claim이 일치하므로 3자 정합 유지)
```

Option B는 oidcLogin을 사용하지만 seededUserId를 claim과 일치시키므로 lesson 05 "우회 금지" 취지를 만족한다. `identityService.invite()`가 DB에 실 User를 생성하고, grant도 그 id로 심기 때문에 집계가 실제로 검증된다.

### Sampling Rate

- **태스크 커밋 시:** `./gradlew test --tests *BffAuthMeIT* --tests *ArchitectureTest* -x`
- **Wave 머지 시:** `./gradlew test --rerun-tasks`
- **Phase gate:** Full suite green 후 `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `src/test/java/com/anchors/baseline/auth/BffAuthMeIT.java` — AUTH-08/09 통합 단언 (Wave 3에서 신설)
- [ ] `identity/application/UserView.java` — Wave 1 신규 read DTO
- [ ] `authorization/application/PermissionView.java` (+ `MenuView`, `ResourceView` 중첩 record) — Wave 1 신규 read DTO

---

## D-06 ArchUnit 명시성 — 코드 실측 권고

### 실측 결과

`ArchitectureTest.hexagonalLayerDependencies()`는 **단일** `layeredArchitecture` 규칙이다:

```java
// [VERIFIED: 코드 실측]
layeredArchitecture()
    .consideringOnlyDependenciesInLayers()
    .layer("Interfaces").definedBy(ROOT + "..interfaces..")   // 접미 글로브, 컨텍스트 무관
    .layer("Application").definedBy(ROOT + "..application..")
    ...
    .whereLayer("Interfaces").mayOnlyAccessLayers("Application", "Domain")
```

`auth.interfaces` → `identity.application` + `authorization.application` 호출은 단지 "Interfaces 레이어 → Application 레이어" 이동이다. 컨텍스트 슬라이스 규칙이 전혀 없다.

**선례:** `auth.application.IdentityLinkService`가 `identity.application.IdentityApplicationService`를 import·호출하며 이미 GREEN 통과 중이다(Phase 3, 코드 실측).

### 권고: (a) 무변경 + GREEN 단언 테스트 추가

AUTH-11 요구사항 "명시적으로 허용된 규칙으로 통과"의 의도를 최소 비용으로 만족하는 방법:

- 신규 import가 추가된 뒤 `./gradlew test --tests *ArchitectureTest*`를 실행해 GREEN을 **실증**한다(lesson 03/04/05 — 주장이 아닌 테스트로).
- 이 실증이 AUTH-11 SC#3를 충족한다.

**컨텍스트 슬라이스 규칙 신설은 권장하지 않는다.** 이유:
1. baseline 전체(4 컨텍스트)에 영향을 주는 광역 변경이라 "내 정보 집계" phase 범위를 초과한다.
2. 슬라이스 규칙도 third-party 누수를 못 잡는 사각이 존재한다(lesson 04 P1 계승).
3. 현재 규칙이 실제 잘못된 의존을 허용하고 있지 않다 — 오탐 없이 동작 중.

---

## State of the Art

| 구 접근 | 현재 접근 | 변경 시점 | 영향 |
|---------|-----------|-----------|------|
| `AuthorizationApplicationService`는 쓰기 전용 | read 메서드 신설(D-02) | Phase 6 신설 | application이 CQRS-lite read 경로를 갖게 됨 |
| `IdentityApplicationService`는 쓰기 전용 | read 메서드 신설(D-01) | Phase 6 신설 | 동상 |
| `/api/auth`는 session/login/logout만 | me() 추가 | Phase 6 신설 | SPA가 단일 요청으로 신원+권한 획득 |

**Deprecated/Outdated:**
- `Map.of()` 기반 응답 DTO → Phase 5 lesson으로 record+@JsonInclude로 고정. me() 응답도 동일.

---

## Environment Availability

> Phase 6는 순수 코드 변경. 기존 Testcontainers(postgres:16, redis:7)와 WireMock 인프라를 그대로 재사용한다. 신규 외부 도구 없음.

| 의존성 | 필요 이유 | 가용 여부 | 버전 | 비고 |
|--------|-----------|-----------|------|------|
| Testcontainers (PostgreSQL 16) | 실 DB 권한 시드·조회 검증 | ✓ | AbstractIntegrationTest 확인 | 싱글턴 패턴 재사용 |
| Testcontainers (Redis 7) | 세션 저장 | ✓ | 동상 | |
| WireMock (MockOidcServer) | 실 로그인 흐름 | ✓ | BffAuthSessionIT 확인 | `OIDC.start()` 재사용 |
| ArchUnit | 계층 규칙 검증 | ✓ | ArchitectureTest 확인 | |

---

## Security Domain

> `security_enforcement: true`, `security_asvs_level: 1` (config.json 확인) — 이 섹션 필수.

### Applicable ASVS Categories

| ASVS Category | 적용 여부 | 표준 통제 |
|---------------|-----------|-----------|
| V2 Authentication | YES | `/api/auth/me`는 기존 인증 매처에 걸림 — 무변경 |
| V3 Session Management | YES | 기존 Redis 세션 유지 — 무변경 |
| V4 Access Control | YES | `default-deny` + read-only(쓰기 없음) — 신규 위험 없음 |
| V5 Input Validation | YES | userId = principal attribute (신뢰된 서버 세션 출처). 외부 입력 없음. |
| V6 Cryptography | NO | me()는 암호화 미사용 |

### 위협 패턴

| 패턴 | STRIDE | 표준 완화 |
|------|--------|-----------|
| 타인의 me() 호출 (userId 조작) | Spoofing | userId 출처 = 서버 세션 principal attribute (신뢰됨). URL/쿼리파라미터로 userId 받지 않음. |
| 비인증 권한 열람 | Information Disclosure | `/api/**` 보호 매처 401. SecurityConfig 무변경. |
| 그룹 부여 grant.groupId를 userId로 혼동 | Elevation of Privilege | `findByUserId`만 호출. `findByGroupId` 미사용. |

---

## Open Questions (RESOLVED)

1. **SC#2 "권한 전체" 해석 — direct only vs 그룹 상속 포함**
   - **RESOLVED:** direct 부여만 채택(D-02). findEffectiveGrants(userId, resourceId)는 resourceId 필수라 전체 열거 불가 — effective 열거는 신규 CTE(범위 초과). MeResponse 3버킷 구조는 향후 그룹/계층 상속 추가가 가능하도록 유지하고 한계를 코드 주석/SUMMARY에 명시.

2. **미존재 User HTTP 매핑 — 404 vs 500**
   - **RESOLVED:** 정상 경로는 항상 존재(Phase 3 신원 연결 전제). `userRepository.findById(userId).orElseThrow(IllegalArgumentException)` → Spring 기본 500. 방어적 처리로 충분(planner 06-01 확정).

3. **BffAuthMeIT — 별도 클래스 vs BffAuthSessionIT 확장**
   - **RESOLVED:** 별도 `BffAuthMeIT` 클래스 신설(planner 06-03 확정). 테스트 책임 분리 + BffAuthSessionIT SC#2 흐름과 무충돌.

---

## Assumptions Log

> 코드 실측으로 확인된 사항이 대부분이라 가정 항목이 최소다.

| # | 주장 | 섹션 | 틀릴 경우 영향 |
|---|------|------|----------------|
| A1 | `GroupMemberRepository.findByUserId`가 존재한다 (코드는 읽지 않았으나 `GroupMemberRepository.deleteByGroupIdAndUserId` 실측 + `addMember(groupId, userId)` 패턴으로 추론) | D-02 A-확장 경로 | 그룹 상속 포함 시 신규 메서드 추가 필요 — 단, direct 채택이라 무관 |

**주요 주장 검증 상태:**
- `findByUserId` 3종 존재: [VERIFIED: 소스 코드 실측]
- `UserRepository.findById`: [VERIFIED: 소스 코드 실측]
- ArchUnit 단일 전역 글로브 규칙: [VERIFIED: 소스 코드 실측]
- `IdentityLinkService` 교차 컨텍스트 선례: [VERIFIED: 소스 코드 실측]
- `AuthorizationApplicationService` read 메서드 0건: [VERIFIED: 소스 코드 실측]
- `IdentityApplicationService` read 메서드 0건: [VERIFIED: 소스 코드 실측]

---

## Sources

### Primary (HIGH confidence — 소스 코드 직접 실측)

- `authorization/application/AuthorizationApplicationService.java` — read 메서드 0건 확인, 6개 쓰기 포트 목록
- `authorization/domain/repository/GlobalRoleGrantRepository.java` — `findByUserId(long)` 존재 확인
- `authorization/domain/repository/MenuGrantRepository.java` — `findByUserId(long)` 존재 확인 (`findByGroupId` 별도)
- `authorization/domain/repository/ResourceGrantRepository.java` — `findByUserId(long)` 존재 확인 (`findByGroupId` 별도)
- `authorization/application/PermissionReadPort.java` — `findEffectiveGrants(userId, resourceId)` 시그니처 확인 (리소스 단위 — 전체 열거 불가)
- `authorization/application/PermissionEvaluator.java` — `readPort.findEffectiveGrants(userId, resource.value())` 호출 패턴
- `mybatis/AuthorizationMapper.xml` — CTE 구조 확인 (findEffectiveGrants는 `#{resourceId}` 필수 파라미터)
- `identity/application/IdentityApplicationService.java` — read 메서드 0건, `invite`/`linkIdentity`/`disable` 3종만
- `identity/domain/model/User.java` — `@Getter`, `getId()/getEmail()/getStatus()`, `Email.getValue()`, `UserStatus` enum
- `auth/interfaces/AuthController.java` — `SessionResponse` record 패턴, `OidcUser.getAttribute("user_id")`, `@JsonInclude(NON_NULL)`
- `architecture/ArchitectureTest.java` — 단일 `layeredArchitecture` 전역 글로브 규칙, 컨텍스트 슬라이스 규칙 부재
- `auth/application/IdentityLinkService.java` — `identity.application` 교차 컨텍스트 import 선례
- `auth/BffAuthSessionIT.java` — WireMock 실 로그인 인프라, `uniqueEmail`/`uniqueOid` 동적 생성 패턴
- `AbstractIntegrationTest.java` — postgres:16 + redis:7 싱글턴 컨테이너
- `.planning/config.json` — `nyquist_validation: true`, `security_enforcement: true`

### Secondary (MEDIUM confidence — lesson 파일 기반)

- `.planning/lessons/04-2026-05-31.md` — @Param 누수 회피, 재귀 CTE 실 Postgres 필수, 투기적 VO 금지
- `.planning/lessons/05-2026-05-31.md` — oidcLogin 우회 금지, record+@JsonInclude, 환경 종속 하드코딩 금지

---

## Metadata

**Confidence breakdown:**

| 영역 | 신뢰도 | 근거 |
|------|--------|------|
| Standard Stack (재사용 포트) | HIGH | 소스 코드 직접 실측 — findByUserId 3종, findById 모두 확인 |
| D-02 권고 (direct 채택) | HIGH | CTE 시그니처 실측 (resourceId 필수) + findByUserId 즉시 재사용 가능 확인 |
| D-06 권고 (ArchUnit 무변경) | HIGH | ArchitectureTest.java 전문 실측 + IdentityLinkService 선례 |
| Validation Architecture | HIGH | AbstractIntegrationTest + BffAuthSessionIT 인프라 실측 |
| Security | MEDIUM | ASVS L1 적용 판단은 추론 기반 (코드 보안 로직 실측 일부) |

**Research date:** 2026-05-31
**Valid until:** 2026-06-30 (stable stack — 30일)
