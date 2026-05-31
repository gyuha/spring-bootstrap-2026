---
status: complete
phase: 03-bff-auth
source: [03-01-SUMMARY.md, 03-02-SUMMARY.md, 03-03-SUMMARY.md]
started: 2026-05-31T08:12:28Z
updated: 2026-05-31T08:12:28Z
---

## Current Test

[testing complete]

## Tests

### 1. OIDC 로그인 흐름 (AUTH-01)
expected: WireMock OIDC IdP로 authorization-code 흐름을 태우면 인증 성공으로 귀결되고 세션이 생성된다.
result: pass
evidence: "BffAuthIT.oidcLoginFlowSucceeds GREEN (WireMock 토큰 교환, jwks 서명 id_token + nonce 검증)."

### 2. 토큰 Redis 서버세션 only, 쿠키엔 SESSION만 (AUTH-02/SC#1 — 핵심)
expected: 로그인 응답 Set-Cookie에 SESSION만 있고 토큰 문자열이 없으며, 캡처한 SESSION id로 Spring Session을 조회하면 HttpSession attribute(AUTHORIZED_CLIENTS)에 비어있지 않은 access token이 존재한다.
result: pass
evidence: "BffAuthIT.tokenOnlyInRedisNotInCookie GREEN (WireMock 경로, sessionRepository.findById → AUTHORIZED_CLIENTS access token 단언, OAuth2AuthorizedClientService 미사용). NFR-02 충족 — HttpSessionOAuth2AuthorizedClientRepository 빈 명시 배선(기본 InMemory 교정)."

### 3. 쿠키 세션만으로 인증 API (AUTH-03/SC#2)
expected: 쿠키 기반 세션만으로 /api/me가 200을 반환하고 응답 userId가 로컬 User.id다.
result: pass
evidence: "BffAuthIT.cookieSessionAuthenticatesApi GREEN (oidcLogin)."

### 4. 로그아웃 시 세션 무효화 (AUTH-04/SC#4)
expected: WireMock으로 수립한 실제 세션을 로그아웃하면 Redis 세션이 삭제되고 동일 SESSION 쿠키 재요청이 401/redirect로 실패한다.
result: pass
evidence: "BffAuthIT.logoutInvalidatesSession GREEN (WireMock 세션 라운드트립, logout 후 401 + sessionRepository.findById null). CSRF: CsrfCookieFilter로 XSRF 쿠키 materialize."

### 5. 최초 로그인 시 linkIdentity (AUTH-05/SC#3)
expected: 최초 로그인 시 linkIdentity가 호출되어 INVITED→ACTIVE 전이가 일어난다. 재로그인은 멱등(미호출).
result: pass
evidence: "BffAuthIT.firstLoginLinksIdentity (DB ACTIVE+externalId 단언) + reLoginIsIdempotent GREEN (Postgres Testcontainer)."

### 6. 미초대 사용자 거부 (D-04)
expected: 초대되지 않은 email로 로그인 시 인증 실패(OAuth2AuthenticationException).
result: pass
evidence: "BffAuthIT 미초대 거부 단언 GREEN."

### 7. BaselineOidcUser 직렬화 라운드트립 (Pitfall 2)
expected: JDK 직렬화 라운드트립 후 localUserId·user_id attribute 보존(Redis 세션 직렬화 경로 가드).
result: pass
evidence: "BaselineOidcUserSerializationTest GREEN (먼저 도는 단위 가드)."

### 8. 계층 게이트 + 전체 스위트
expected: auth 패키지 ArchUnit 위반 0, 전체 스위트 GREEN(Phase 1·2 회귀 없음).
result: pass
evidence: "ArchitectureTest GREEN; ./gradlew test --rerun-tasks → 28 tests, 0 failures, 0 errors."

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
