# Phase 6: 내 정보 신원·권한 집계 - Pattern Map

**작성일:** 2026-05-31
**분석 대상 파일:** 7개 (신규/수정)
**Analog 확보:** 7/7

---

## 파일 분류

| 신규/수정 파일 | 역할 | 데이터 흐름 | 가장 가까운 Analog | 매칭 품질 |
|---|---|---|---|---|
| `auth/interfaces/AuthController.java` (수정) | controller | request-response | 자기 자신의 `session()` 메서드 (lines 51-57) | exact |
| `identity/application/UserView.java` (신규) | read DTO | transform | `authorization/application/EffectiveGrantDto.java` | role-match |
| `identity/application/IdentityApplicationService.java` (수정) | service | CRUD(read) | 자기 자신의 `linkIdentity`/`disable` 메서드 + `findById` 패턴 (lines 39-43) | exact |
| `authorization/application/PermissionView.java` (신규) | read DTO | transform | `authorization/application/EffectiveGrantDto.java` | role-match |
| `authorization/application/AuthorizationApplicationService.java` (수정) | service | CRUD(read) | 자기 자신의 `grantGlobalRole`/`revokeGlobalRole` 패턴 + 3개 repository 주입 | exact |
| `auth/interfaces/MeResponse.java` (신규, AuthController 내부 record) | response DTO | request-response | `auth/interfaces/AuthController.java` `SessionResponse` record (lines 96-99) | exact |
| `test/auth/BffAuthMeIT.java` (신규) | integration test | request-response | `test/auth/BffAuthSessionIT.java` | exact |

---

## Pattern Assignments

### `auth/interfaces/AuthController.java` — `me()` 메서드 추가 (controller, request-response)

**Analog:** `src/main/java/com/anchors/baseline/auth/interfaces/AuthController.java` — `session()` 메서드

**임포트 패턴** (lines 1-16):
```java
package com.anchors.baseline.auth.interfaces;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
// 추가 필요: IdentityApplicationService, AuthorizationApplicationService 임포트
```

**핵심 패턴 — session() 계승, me() 적용** (lines 51-57):
```java
// session() — me()가 계승할 기준 패턴
@GetMapping("/session")
public SessionResponse session(@AuthenticationPrincipal OidcUser principal) {
    if (principal == null) {
        return new SessionResponse(false, null);
    }
    return new SessionResponse(true, principal.getAttribute("user_id"));
}

// me() 추가 패턴 (session()과의 차이: null 분기 없음 — SecurityConfig가 401 먼저)
@GetMapping("/me")
public MeResponse me(@AuthenticationPrincipal OidcUser principal) {
    // infrastructure BaselineOidcUser 미참조 — OidcUser.getAttribute만 사용(D-04)
    Object rawId = principal.getAttribute("user_id");
    long userId = ((Number) rawId).longValue();  // Number 상위 캐스팅으로 Integer/Long 모두 안전

    UserView identity = identityService.findUser(userId);
    PermissionView permissions = authorizationService.findPermissions(userId);
    return MeResponse.of(identity, permissions);
}
```

**주의 사항:**
- `me()`는 `session()`과 달리 `principal == null` 분기 없음: D-05 — SecurityConfig `/api/**` 보호 매처가 비인증 401을 먼저 처리
- `IdentityApplicationService`·`AuthorizationApplicationService` 생성자 주입 추가 필요
- `auth/interfaces` → `identity/application`·`authorization/application` 교차 컨텍스트 임포트는 ArchUnit `Interfaces → Application` 전역 레이어 글로브로 이미 허용(D-06, IdentityLinkService 선례)

**응답 record 패턴 — SessionResponse 계승** (lines 95-99):
```java
/** 세션 상태 응답. userId 는 비인증 시 null 이며 {@code NON_NULL} 로 직렬화에서 생략된다(Map.of NPE 회피). */
public record SessionResponse(
        boolean authenticated,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object userId) {
}
// → MeResponse는 항상 존재하는 필드라 NON_NULL 불필요. 빈 컬렉션은 [] 직렬화(null 아님)
```

---

### `identity/application/UserView.java` — 신규 read DTO (read DTO, transform)

**Analog:** `src/main/java/com/anchors/baseline/authorization/application/EffectiveGrantDto.java`

**EffectiveGrantDto 패턴** (lines 1-20):
```java
package com.anchors.baseline.authorization.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 읽기 모델 DTO (CQRS-lite) — evaluate 합산의 raw grant 한 행. 평탄 프로젝션(Pitfall 5).
 * record 가 아니라 클래스다 — MyBatis 어댑터(Wave 2)가 무인자 생성자로 매핑한다(SampleDto 형판).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveGrantDto {
    private String role;
    private Long resourceId;
    private String source;
}
```

**UserView 적용 패턴 (direct 해석 — JPA 파생 쿼리만, MyBatis 없음):**
```java
package com.anchors.baseline.identity.application;

/**
 * Identity 컨텍스트 신원 읽기 DTO (CQRS-lite). User 엔티티를 interfaces 계층에 노출하지 않는다.
 * JPA 전용 경로 — MyBatis 매핑 없음. record 사용 가능(무인자 생성자 불필요).
 */
public record UserView(Long id, String email, String status) {}
// status = UserStatus.name() (INVITED/ACTIVE/DISABLED 문자열 — SPA 친화)
// @NoArgsConstructor/@Data 불필요: JPA findById → 애플리케이션 레벨 변환, MyBatis 비사용
```

**주의:** `EffectiveGrantDto`는 MyBatis 무인자 생성자가 필요해 class 사용. `UserView`는 JPA 파생 쿼리 결과를 애플리케이션에서 변환하므로 **record 사용 가능**(lesson 05 record+@JsonInclude 정공법).

---

### `identity/application/IdentityApplicationService.java` — `findUser()` 메서드 추가 (service, CRUD read)

**Analog:** 자기 자신 (`src/main/java/com/anchors/baseline/identity/application/IdentityApplicationService.java`)

**기존 클래스 레벨 트랜잭션 패턴** (lines 1-18):
```java
@Service
@RequiredArgsConstructor
@Transactional  // 클래스 레벨 = 쓰기 기본값 (readOnly = false)
public class IdentityApplicationService {

    private final UserRepository userRepository;
    // ...
```

**findById 기반 조회 패턴 — linkIdentity/disable에서 계승** (lines 38-43):
```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
// → findUser()는 User 반환 대신 UserView로 평탄화해 반환
```

**신규 read 메서드 적용 패턴:**
```java
// 메서드 레벨 readOnly 오버라이드 — 클래스 레벨 @Transactional(쓰기)보다 구체적이라 우선 적용
@Transactional(readOnly = true)
public UserView findUser(long userId) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
    return new UserView(user.getId(), user.getEmail().getValue(), user.getStatus().name());
}
// user.getEmail().getValue() — Email VO의 평탄화(User.java line 33-34 @Embedded Email)
// user.getStatus().name()  — UserStatus enum → 문자열 (INVITED/ACTIVE/DISABLED)
```

**User 엔티티 getter 실측** (`identity/domain/model/User.java` lines 27-44):
- `user.getId()` — `@Id @GeneratedValue Long id`
- `user.getEmail()` — `@Embedded Email` VO → `.getValue()`로 String
- `user.getStatus()` — `@Enumerated UserStatus` enum → `.name()`으로 String

---

### `authorization/application/PermissionView.java` — 신규 read DTO (read DTO, transform)

**Analog:** `src/main/java/com/anchors/baseline/authorization/application/EffectiveGrantDto.java` + 도메인 모델 실측

**도메인 모델 getter 실측:**
- `GlobalRoleGrant.getRole()` — `RoleName` enum → `.name()` String
- `MenuGrant.getMenuId()` / `MenuGrant.getRole()` — `Long menuId` / `RoleName` enum
- `ResourceGrant.getResourceId()` / `ResourceGrant.getRole()` — `Long resourceId` / `RoleName` enum

**신규 PermissionView 패턴 (record, JPA 전용 경로):**
```java
package com.anchors.baseline.authorization.application;

import java.util.List;

/**
 * Authorization 컨텍스트 직접 부여 권한 읽기 DTO (CQRS-lite, D-02 direct 해석).
 * 도메인 엔티티(GlobalRoleGrant/MenuGrant/ResourceGrant)를 interfaces 계층에 노출하지 않는다.
 * 빈 컬렉션은 null 아닌 [] — SPA가 length 분기.
 */
public record PermissionView(
        List<String> roles,
        List<MenuEntry> menus,
        List<ResourceEntry> resources) {

    public record MenuEntry(long menuId, String role) {}
    public record ResourceEntry(long resourceId, String role) {}
}
// RoleName enum → .name() String 평탄화
// groupId 기반 MenuGrant/ResourceGrant 제외 (findByUserId만 — D-02 direct)
```

---

### `authorization/application/AuthorizationApplicationService.java` — `findPermissions()` 메서드 추가 (service, CRUD read)

**Analog:** 자기 자신 (`src/main/java/com/anchors/baseline/authorization/application/AuthorizationApplicationService.java`)

**기존 repository 주입 패턴** (lines 29-36):
```java
@Service
@RequiredArgsConstructor
@Transactional  // 클래스 레벨 = 쓰기 기본값
public class AuthorizationApplicationService {

    private final GlobalRoleGrantRepository globalRoleGrantRepository;
    private final MenuGrantRepository menuGrantRepository;
    private final ResourceGrantRepository resourceGrantRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ResourceHierarchyRepository resourceHierarchyRepository;
```

**findByUserId 포트 시그니처 실측:**
- `GlobalRoleGrantRepository.findByUserId(long userId)` → `List<GlobalRoleGrant>` (line 17)
- `MenuGrantRepository.findByUserId(long userId)` → `List<MenuGrant>` (line 18)
- `ResourceGrantRepository.findByUserId(long userId)` → `List<ResourceGrant>` (line 18)

**신규 read 메서드 적용 패턴:**
```java
@Transactional(readOnly = true)
public PermissionView findPermissions(long userId) {
    List<String> roles = globalRoleGrantRepository.findByUserId(userId).stream()
            .map(g -> g.getRole().name())
            .toList();
    List<PermissionView.MenuEntry> menus = menuGrantRepository.findByUserId(userId).stream()
            .map(g -> new PermissionView.MenuEntry(g.getMenuId(), g.getRole().name()))
            .toList();
    List<PermissionView.ResourceEntry> resources = resourceGrantRepository.findByUserId(userId).stream()
            .map(g -> new PermissionView.ResourceEntry(g.getResourceId(), g.getRole().name()))
            .toList();
    return new PermissionView(roles, menus, resources);
}
// findByGroupId 절대 호출 금지 — direct 해석(D-02). 그룹 부여는 group 기반 별도 메서드
// 빈 결과 = 빈 List(권한 없는 사용자) — NullPointerException 없음
```

---

### `auth/interfaces/MeResponse.java` (AuthController 내부 record) — 응답 DTO (response DTO, request-response)

**Analog:** `src/main/java/com/anchors/baseline/auth/interfaces/AuthController.java` `SessionResponse` record (lines 95-99)

**SessionResponse 계승 패턴:**
```java
/** 세션 상태 응답. userId 는 비인증 시 null 이며 {@code NON_NULL} 로 직렬화에서 생략된다(Map.of NPE 회피). */
public record SessionResponse(
        boolean authenticated,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object userId) {
}
```

**MeResponse 적용 패턴 (record + 빈 컬렉션 정책):**
```java
// AuthController 내부 static record (또는 별도 파일) — auth/interfaces 소유
public record MeResponse(
        Long userId,
        String email,
        String status,
        List<String> roles,
        List<PermissionView.MenuEntry> menus,
        List<PermissionView.ResourceEntry> resources) {

    // 정적 팩토리 — interfaces가 두 application DTO를 받아 하나로 조립
    public static MeResponse of(UserView identity, PermissionView permissions) {
        return new MeResponse(
                identity.id(),
                identity.email(),
                identity.status(),
                permissions.roles(),
                permissions.menus(),
                permissions.resources());
    }
}
// @JsonInclude(NON_NULL): 스칼라 필드 없음 — 모두 항상 존재. 빈 컬렉션은 [] 직렬화
// MeResponse 타입을 auth/interfaces에 둠 — 응답 계약은 interfaces 소유(D-03)
```

---

### `test/auth/BffAuthMeIT.java` — 통합 테스트 (integration test, request-response)

**Analog:** `src/test/java/com/anchors/baseline/auth/BffAuthSessionIT.java`

**AbstractIntegrationTest + WireMock 셋업 패턴** (lines 44-83):
```java
@AutoConfigureMockMvc
class BffAuthMeIT extends AbstractIntegrationTest {

    private static final MockOidcServer OIDC = new MockOidcServer();

    static {
        OIDC.start();  // issuer-uri eager discovery 전에 WireMock 기동
    }

    @DynamicPropertySource
    static void oidcRegistration(DynamicPropertyRegistry registry) {
        String base = OIDC.baseUrl();
        String reg = "spring.security.oauth2.client.registration.test-idp.";
        registry.add(reg + "client-id", () -> "test-client-id");
        // ... (BffAuthSessionIT lines 57-71 동일)
    }

    @Autowired MockMvc mvc;
    @Autowired IdentityApplicationService identityService;
    @Autowired AuthorizationApplicationService authorizationService;  // 권한 시드용 추가
    @Autowired SessionRepository<? extends Session> sessionRepository;
    @LocalServerPort int port;
    private final RestTemplate rest = noRedirectRestTemplate();
```

**실 User 시드 + oidcLogin claim 일치 전략 (Option B — D-07):**
```java
@Test
void meReturnsIdentity() throws Exception {
    // 1. 실 User 시드
    String email = uniqueEmail("sc1");
    Long seededUserId = identityService.invite(email);

    // 2. 시드 userId와 claim 일치 (3자 정합: principal.user_id == seededUserId == grant.userId)
    mvc.perform(get("/api/auth/me")
                    .with(oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(seededUserId))
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.status").value("INVITED"));
}

@Test
void meUnauthenticatedReturns401() throws Exception {
    mvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
}

@Test
void meReturnsGrantedPermissions() throws Exception {
    String email = uniqueEmail("sc2");
    Long seededUserId = identityService.invite(email);

    // 실 Postgres에 직접 grant 시드 — D-07 "실 Postgres 권한 시드"
    authorizationService.grantGlobalRole(seededUserId, RoleName.ADMIN);
    authorizationService.grantMenuToUser(seededUserId, 1L, RoleName.EDITOR);
    authorizationService.grantResourceToUser(seededUserId, 10L, RoleName.VIEWER);

    mvc.perform(get("/api/auth/me")
                    .with(oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
            .andExpect(jsonPath("$.menus[0].menuId").value(1))
            .andExpect(jsonPath("$.resources[0].resourceId").value(10));
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
```

**uniqueEmail/uniqueOid 패턴 — 환경 종속 하드코딩 금지 (lesson 05)** (BffAuthSessionIT lines 354-360):
```java
private static String uniqueEmail(String tag) {
    return tag + "-" + System.nanoTime() + "@example.com";
}

private static String uniqueOid() {
    return "oid-" + System.nanoTime();
}
```

**noRedirectRestTemplate 패턴** (BffAuthSessionIT lines 288-309): WireMock 실 로그인이 필요한 경우 그대로 복사.

**permitAll 회귀 단언 추가 (D-05 — me는 permitAll 제외):**
```java
@Test
void meIsNotPermitAll() throws Exception {
    // /api/auth/me 는 /api/** 보호 매처에 걸림 — permitAll에 추가하면 안 됨
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    // /api/auth/session 은 여전히 permitAll
    mvc.perform(get("/api/auth/session")).andExpect(status().isOk());
}
```

---

## 공유 패턴 (Cross-Cutting)

### 1. readOnly 트랜잭션 오버라이드

**출처:** `identity/application/IdentityApplicationService.java` (line 17: `@Transactional`) + Spring 표준
**적용 대상:** `IdentityApplicationService.findUser()`, `AuthorizationApplicationService.findPermissions()`

```java
// 클래스 레벨 @Transactional(쓰기)가 있는 서비스에 read 메서드 추가 시
@Transactional(readOnly = true)  // 메서드 레벨이 클래스 레벨을 오버라이드
public UserView findUser(long userId) { ... }
```

### 2. infrastructure 타입 미참조 — OidcUser.getAttribute만

**출처:** `auth/interfaces/MeController.java` (lines 20-23) + `auth/interfaces/AuthController.java` (line 56)
**적용 대상:** `AuthController.me()`

```java
// MeController 실측 패턴 (lines 20-23)
@GetMapping("/api/me")
public Map<String, Object> me(@AuthenticationPrincipal OidcUser principal) {
    return Collections.singletonMap("userId", principal.getAttribute("user_id"));
}

// AuthController.session() 실측 패턴 (line 56)
return new SessionResponse(true, principal.getAttribute("user_id"));

// me() 적용 — Number 상위 캐스팅(RESEARCH.md Pitfall 5)
Object rawId = principal.getAttribute("user_id");
long userId = ((Number) rawId).longValue();
```

### 3. 교차 컨텍스트 application 호출 선례 (ArchUnit 통과 증명)

**출처:** `auth/application/IdentityLinkService.java` (lines 3-4, 25):
```java
import com.anchors.baseline.identity.application.IdentityApplicationService;
// ...
private final IdentityApplicationService identityApplicationService;
```
`auth.application` → `identity.application` import가 ArchUnit `Application → Application`(같은 레이어)으로 통과.

`auth.interfaces` → `identity.application`·`authorization.application`은 `Interfaces → Application` 계층 이동이며 `ArchitectureTest.java` line 39: `.whereLayer("Interfaces").mayOnlyAccessLayers("Application", "Domain")`으로 이미 허용.

### 4. 도메인 엔티티 → application DTO 평탄화 패턴

**출처:** `authorization/application/EffectiveGrantDto.java` — 순수 DTO, 도메인 엔티티 미노출
**적용 대상:** `UserView`, `PermissionView`

```java
// 틀린 패턴: interfaces가 도메인 엔티티 직접 사용
UserView -> User    // JPA @Entity 노출
PermissionView -> GlobalRoleGrant  // JPA @Entity 노출

// 올바른 패턴: application이 평탄 record로 변환 후 interfaces에 전달
user.getEmail().getValue()  // Email VO → String
grant.getRole().name()      // RoleName enum → String
```

### 5. 통합 테스트 시드 전략 — 3자 일치

**출처:** `BffAuthSessionIT.java` + RESEARCH.md D-07
**적용 대상:** `BffAuthMeIT.java` 모든 테스트

```
principal.getAttribute("user_id") == identityService.invite(email) 반환값 == grant.userId
```

`identityService.invite(email)` 반환 `Long seededUserId`를 `oidcLogin().userInfoToken(u -> u.claim("user_id", seededUserId))`와 일치시키고, 동일 `seededUserId`로 grant를 시드한다. 합성 userId(예: 42L 하드코딩) 절대 사용 금지(lesson 05).

---

## Analog 없음 (No Analog Found)

이 Phase에서 완전히 새로운 패턴은 없다. 모든 파일에 기존 codebase analog가 존재한다.

---

## Metadata

**Analog 탐색 범위:**
- `src/main/java/com/anchors/baseline/auth/interfaces/`
- `src/main/java/com/anchors/baseline/auth/application/`
- `src/main/java/com/anchors/baseline/identity/application/`
- `src/main/java/com/anchors/baseline/authorization/application/`
- `src/main/java/com/anchors/baseline/authorization/domain/model/`
- `src/main/java/com/anchors/baseline/authorization/domain/repository/`
- `src/test/java/com/anchors/baseline/auth/`
- `src/test/java/com/anchors/baseline/`
- `src/test/java/com/anchors/baseline/architecture/`

**스캔 파일 수:** 14개

**패턴 추출일:** 2026-05-31
