---
phase: 03-bff-auth
plan: 03
type: summary
wave: 3
status: complete
date: 2026-05-31
---

# 03-03 SUMMARY — BFF 인증 SC#1~4 행위 단언 (FINAL wave)

## 결과 한 줄

`./gradlew test` **BUILD SUCCESSFUL — 28 tests, 0 failures** (independent `--rerun-tasks` 재확인 통과). BffAuthIT 7개(SC#1~4·AUTH-01~05·D-04) 전부 GREEN. SC#1·SC#4·AUTH-01 은 WireMock 실제 authorization-code 토큰 교환 경로로 증명.

## 생성/수정 파일

생성(test):
- `src/test/java/com/anchors/baseline/auth/BaselineOidcUserSerializationTest.java` — Pitfall 2 가드(JDK 직렬화 라운드트립, Spring 무관 단위).
- `src/test/java/com/anchors/baseline/auth/support/MockOidcServer.java` — WireMock OIDC IdP fixture(discovery/token/jwks/userinfo, RSA jwks 서명 id_token, nonce echo).
- `src/test/java/com/anchors/baseline/auth/BffAuthIT.java` — SC#1~4·AUTH-01~05·D-04 행위 단언.

수정(test):
- `src/test/resources/application-test.yml` — `spring.session.store-type: redis` 명시 추가. (OIDC registration 은 공유 yml 이 아니라 BffAuthIT `@DynamicPropertySource` 에만 둠 — 아래 deviation 2 참조.)

수정(main, deviation — 아래 참조):
- `src/main/java/com/anchors/baseline/auth/infrastructure/security/SecurityConfig.java`
  - `HttpSessionOAuth2AuthorizedClientRepository` 빈 추가.
  - CSRF 에 `CsrfTokenRequestAttributeHandler` + `CsrfCookieFilter`(static) 추가.

## 전체 테스트 결과 (evidence)

```
$ ./gradlew test            → BUILD SUCCESSFUL
$ ./gradlew test --rerun-tasks (5 tasks executed) → BUILD SUCCESSFUL
```

| Suite | tests | fail | err |
|-------|------:|-----:|----:|
| identity.domain.model.UserTest | 6 | 0 | 0 |
| architecture.ArchitectureTest | 1 | 0 | 0 |
| auth.BaselineOidcUserSerializationTest | 1 | 0 | 0 |
| platform.PersistenceIntegrationTest | 2 | 0 | 0 |
| **auth.BffAuthIT** | **7** | **0** | **0** |
| identity.UserLifecycleIT | 6 | 0 | 0 |
| platform.FlywayMigrationTest | 2 | 0 | 0 |
| platform.ActuatorHealthTest | 1 | 0 | 0 |
| platform.VirtualThreadTest | 2 | 0 | 0 |
| **TOTAL** | **28** | **0** | **0** |

ArchitectureTest(계층 위반 0) 포함 — main 변경(CsrfCookieFilter, authorizedClientRepository 빈은 infrastructure)이 계층 게이트를 깨지 않음.

## SC 검증 방식 (WireMock vs oidcLogin)

| 테스트 | SC/Req | 경로 |
|--------|--------|------|
| oidcLoginFlowSucceeds | AUTH-01 | **WireMock** (authorize→callback→token 교환→세션) |
| tokenOnlyInRedisNotInCookie | SC#1/AUTH-02 | **WireMock**. 캡처 SESSION id → `sessionRepository.findById` → HttpSession attr `HttpSessionOAuth2AuthorizedClientRepository.AUTHORIZED_CLIENTS` 의 `OAuth2AuthorizedClient.getAccessToken().getTokenValue()` 가 `mock-access-token...` 로 비어있지 않음. 쿠키엔 토큰 문자열 부재. `OAuth2AuthorizedClientService` 미사용. |
| cookieSessionAuthenticatesApi | SC#2/AUTH-03 | oidcLogin |
| logoutInvalidatesSession | SC#4/AUTH-04 | **WireMock** 실세션 → POST /logout(+CSRF) → 동일 쿠키 재요청 401 + `findById==null`(Redis 삭제) |
| firstLoginLinksIdentity | SC#3/AUTH-05 | **WireMock** → DB INVITED→ACTIVE, externalId==oid |
| reLoginIsIdempotent | AUTH-05 멱등 | **WireMock** (findByExternalId 분기, 재연결 없음, ACTIVE 유지) |
| uninvitedLoginIsRejected | D-04 | **WireMock** (미초대 → 인증 실패 → /api/me 401) |

충실도 하한(SC#1·SC#4·AUTH-01 = WireMock) 충족. SC#2 만 oidcLogin. 멱등/SC#3/D-04 는 WireMock 으로 더 충실히 검증(허용 범위 내 상향).

## Deviations (계획 대비 변경 — 모두 evidence 기반)

**Deviation 1 — main SecurityConfig 변경 (계획은 "test-only" 였음). 근거: 계획의 SC#1 전제가 실제 프레임워크 기본과 달라서 발생.**

- 계획/RESEARCH 는 "Boot 기본이 `HttpSessionOAuth2AuthorizedClientRepository` 라 토큰이 세션 attribute 에 자동 저장"이라고 가정. **실측 결과 거짓.** Boot autoconfig 의 기본 `OAuth2AuthorizedClientRepository` 빈은 `AuthenticatedPrincipalOAuth2AuthorizedClientRepository`(→ `InMemoryOAuth2AuthorizedClientService` 위임)다 [VERIFIED: `OAuth2ClientWebSecurityAutoConfiguration` + `OAuth2LoginAuthenticationFilter` 바이트코드 확인]. 즉 토큰이 **Redis 세션이 아니라 애플리케이션 인메모리**에 저장돼 NFR-02(토큰 Redis 서버 세션 전용)·SC#1 전제를 위반.
  - 진단 증거: WireMock 로그인 후 Redis 세션 hash 필드 스캔 → `AUTHORIZED_CLIENTS` 부재(SPRING_SECURITY_CONTEXT 만).
  - 수정: `SecurityConfig` 에 `HttpSessionOAuth2AuthorizedClientRepository` 빈 1개 추가(프레임워크 기본 컴포넌트, 커스텀 토큰 스토어 아님 — NFR-01 위반 아님). 이후 SC#1 GREEN.
  - 판단: 이건 test 픽스처로 우회 불가능한 **프로덕션 결함**이었음. SC#1 을 "faking GREEN" 하지 않고 baseline 이 NFR-02 를 실제로 충족하도록 최소 수정.

- CSRF: 기존 `CookieCsrfTokenRepository.withHttpOnlyFalse()` 만으로는 deferred 토큰이 materialize 되지 않아 XSRF-TOKEN 쿠키가 응답에 실리지 않음 → SC#4 의 logout POST 가 403(CSRF) 으로 세션이 안 지워짐. (`.csrf().spa()` 는 Security 6.5.1 에 미존재 — 컴파일 실패로 확인.)
  - 수정: `CsrfTokenRequestAttributeHandler`(SPA 가 쿠키 raw 토큰을 헤더로 회신) + `CsrfCookieFilter`(매 요청 토큰 materialize → 쿠키 항상 하달). D-06 표준 SPA CSRF 패턴. 이후 logout 302 → SC#4 GREEN.

**Deviation 2 — OIDC registration 을 application-test.yml(공유)이 아니라 BffAuthIT `@DynamicPropertySource` 에 둠.** 계획은 "registration 정적 필드는 yml, provider URI 만 DynamicPropertySource" 였으나, registration 을 공유 yml 에 두면 provider URI 를 주입하지 않는 다른 통합 테스트(UserLifecycleIT 등)의 ApplicationContext 가 ClientRegistration 구성 실패로 깨짐(13 fail 재현·확인). registration+provider 를 모두 BffAuthIT 의 DynamicPropertySource 로 옮겨 해당 컨텍스트에만 한정 → 전체 28 GREEN.

**Deviation 3 — MockOidcServer 에 `/userinfo` 스텁 추가(계획 명시 3종 외 1종).** `BaselineOidcUserService extends OidcUserService` 는 email scope 때문에 userinfo 를 조회한다. 미스텁 시 `invalid_user_info_response` 로 로그인 실패. userinfo(sub/email/name) 스텁 추가로 해결. 또한 nonce: Spring 이 authorize 요청에 nonce 를 싣고 id_token 의 nonce claim 을 검증 → authorize 리다이렉트에서 nonce 추출해 id_token 에 echo(검증 우회 아님, 충실 통과).

## 비고

- 세션 직렬화는 JDK 기본 유지(Pitfall 1). BaselineOidcUser·OAuth2AuthorizedClient 모두 JDK Serializable 라운드트립 통과(Task 1 가드 + SC#1 실 Redis 왕복으로 실증).
- main 변경 3건은 `gsd-validate`/`secure-phase` 시 Wave-2 산출물 보강으로 기록 권장(SC#1 전제였던 `HttpSessionOAuth2AuthorizedClientRepository` 가 Wave-2 에서 누락됐던 것).
