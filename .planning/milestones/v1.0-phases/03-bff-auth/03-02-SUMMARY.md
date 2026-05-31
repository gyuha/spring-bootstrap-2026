---
phase: 03-bff-auth
plan: 02
type: execute
wave: 2
status: complete
date: 2026-05-31
---

# 03-02 SUMMARY — auth 바운디드 컨텍스트(BFF 인증 와이어링)

Wave 2 실행 완료. auth 컨텍스트 5개 클래스 + application.yml 세션 설정을 추가했다.
Wave 3(통합 테스트·WireMock fixture)는 본 플랜 범위 밖 — 기존 테스트로 회귀만 확인했다.

## 생성/수정 파일

| 파일 | 역할 |
|------|------|
| `src/main/java/com/anchors/baseline/auth/application/IdentityLinkService.java` | (신규) auth.application 브리지. `resolveAndLink(oid,email,displayName)` — D-02 2단계 매칭 + linkIdentity 조율. 미초대 거부는 `OAuth2AuthenticationException`(D-04) |
| `src/main/java/com/anchors/baseline/auth/infrastructure/security/BaselineOidcUser.java` | (신규) `implements OidcUser, Serializable`. delegate attribute 에 `user_id`=로컬 User.id 병합. `getLocalUserId()` 노출 |
| `src/main/java/com/anchors/baseline/auth/infrastructure/security/BaselineOidcUserService.java` | (신규) `@Component extends OidcUserService`. `loadUser` → super → 브리지 호출 → `new BaselineOidcUser(oidc, userId)` |
| `src/main/java/com/anchors/baseline/auth/infrastructure/security/SecurityConfig.java` | (신규) `SecurityFilterChain` — permitAll(actuator/health, login, oauth2, api-docs, swagger-ui), oauth2Login(조건부), logout, csrf, /api 401 entrypoint |
| `src/main/java/com/anchors/baseline/auth/interfaces/MeController.java` | (신규) `GET /api/me` → `principal.getAttribute("user_id")`. infrastructure import/cast 없음 |
| `src/main/resources/application.yml` | (수정) `spring.session.store-type: redis` + `spring.session.redis.namespace: "spring:session:baseline"`. OIDC registration 블록 미추가 |

## 검증 증거

**`./gradlew compileJava test --tests "*ArchitectureTest*" --tests "*ActuatorHealthTest*"` → BUILD SUCCESSFUL**
- ArchitectureTest: tests=1, failures=0 — 계층 위반 0건. `auth.application → identity.application`(Application→Application, ownLayer 허용) 통과. `interfaces → infrastructure` 미발생(MeController 표준 attribute 경로).
- **ActuatorHealthTest: tests=1, failures=0 — GREEN.** Spring Security classpath 진입(lockdown) 회귀 없음. `/actuator/health` permitAll 로 200 UP 유지. **컨텍스트가 OIDC registration 없이 부팅됨**(아래 조건부 oauth2Login 가드 덕분).

**`./gradlew test`(전체) → BUILD SUCCESSFUL** — 7개 클래스 20 테스트, failures=0 errors=0 skipped=0:
ArchitectureTest(1), UserTest(6), UserLifecycleIT(6), ActuatorHealthTest(1), FlywayMigrationTest(2), PersistenceIntegrationTest(2), VirtualThreadTest(2).

**MeController infra import grep:** `OK: no infra import`(무매치).

## 핵심 결정 / 편차

### 편차 1 — oauth2Login 조건부 가드(부팅 회귀 방지, 계획 NOTE 의 권장 경로 채택)
계획대로 main yml 에 OIDC registration 을 두지 않았다. 그러나 `oauth2Login(...)` DSL 은 `ClientRegistrationRepository` 빈을 요구하며, registration 이 없으면 Spring Boot 가 그 빈을 auto-config 하지 않아 `http.build()` 시점에 `NoSuchBeanDefinitionException` 으로 컨텍스트 부팅이 깨진다(Context7 Spring Security 6.5.1 문서로 확인 `[High]`). ActuatorHealthTest 가 바로 이 부팅 경로를 탄다.

→ 계획 NOTE("oauth2Login is auto-skipped when no registrations OR gate it — but do NOT add a real/empty registration")의 **gate it** 경로를 채택: `ObjectProvider<ClientRegistrationRepository>` 주입 후 `getIfAvailable() != null` 일 때만 `http.oauth2Login(...)` 적용. main 프로파일(registration 없음)은 oauth2Login 없이 부팅, test/배포 프로파일(registration 있음)은 oauth2Login 활성. 시크릿·빈 placeholder 추가 없음. NFR 위반 없음.

### 편차 2 — `AntPathRequestMatcher` → `PathPatternRequestMatcher`
401 entrypoint 의 `/api/**` 매처로 처음 `AntPathRequestMatcher` 를 썼으나 Security 6.5.1 에서 deprecated-for-removal 경고가 떴다. 비-deprecated API `PathPatternRequestMatcher.withDefaults().matcher("/api/**")`(jar 에 존재 확인)로 교체 — 경고 제거, 동작 동일.

### 비편차 — 토큰 Redis-only(SC#1)
커스텀 `OAuth2AuthorizedClientService`/토큰 스토어를 만들지 않았다(NFR-01). 프레임워크 기본 `HttpSessionOAuth2AuthorizedClientRepository` 가 토큰을 HttpSession attribute 로 저장 → Spring Session Redis 를 타고 Redis 서버 측에만. 직렬화는 기본 JDK 유지(Pitfall 1 JSON 전환 금지).

## Wave 3 인계 사항
- `BaselineOidcUser` Serializable 라운드트립 가드 테스트(Pitfall 2) — 미작성(03-03 소관).
- BffAuthIT(AUTH-01~05·SC#1~4) + WireMock IdP fixture — 미작성(03-03 소관).
- 조건부 oauth2Login: test 프로파일 application-test.yml 에 WireMock issuer-uri registration 을 정의해야 oauth2Login 이 활성화되고 토큰 교환 경로가 동작한다(03-03 에서 확인 필요).
