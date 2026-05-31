# 05-03 SUMMARY — BffAuthSessionIT 통합 테스트 (SC#1~4)

**Wave:** 3 | **Requirements:** AUTH-06, AUTH-07, AUTH-10 | **Status:** ✅ Complete

## 무엇을 했는가

`auth/BffAuthSessionIT.java` 신규 생성 (7 tests, AbstractIntegrationTest 상속 — Testcontainers postgres:16/redis:7 + WireMock OIDC):

- **SC#1** `sessionUnauthenticatedReturns200()` (MockMvc) — 비인증 200 {authenticated:false}, userId 키 부재. `sessionAuthenticatedReturnsUserId()` — oidcLogin(user_id=42) → 200 {authenticated:true, userId:42}.
- **SC#4** `permitAllMatchers()` — /api/auth/session 200, /api/auth/login 302, /api/me 401(보호 매처 불변).
- **SC#2** `logoutReturns204AndInvalidatesSession()` (WireMock 실 토큰 교환) — POST /api/auth/logout → **204**, `sessionRepository.findById == null`(Redis read-back), 이후 동일 세션 /api/me → 401.
- **SC#3** `loginRedirectsToOAuthEndpoint()` — 302 + Location `/oauth2/authorization/`. `loginWithReturnToRedirectsAfterLogin()` — WireMock 전체 흐름(진입 /api/auth/login?returnTo=/dashboard → authorize → 콜백 → successHandler RETURN_TO 복원) → 최종 Location `/dashboard`. `loginRejectsAbsoluteReturnTo()` — `https://evil.com`·`//evil.com` 모두 Location에 evil.com 미포함.

returnTo 전체 흐름용 `performWireMockLoginViaLoginEndpoint(oid, email, entryUrl)` 오버로드 추가 — 진입 세션을 authorize·콜백까지 캐리해 RETURN_TO 보존(RESEARCH A1 가정 실증: changeSessionId가 attribute 보존).

## 검증

- `./gradlew test --rerun-tasks` → **BUILD SUCCESSFUL** (9s). **총 58 tests, 0 실패/에러** (v1.0 51 + 신규 7).
- BffAuthSessionIT: tests=7, failures=0, errors=0. BffAuthIT(Wave 1/2 회귀 없음), ArchitectureTest GREEN(D-06).
- 라이브 구동 없이 전부 Testcontainers/WireMock/MockMvc로 SC#1~4 행위 단언(포트 혼잡 회피). 로그아웃 후 인증실패는 서버측 Redis 세션 read-back으로 단언(Phase 3 lesson).

## 비고

- RESEARCH A1 가정("returnTo 세션 저장이 BaselineOidcUserService 흐름과 충돌 없음")이 `loginWithReturnToRedirectsAfterLogin()` GREEN으로 실증됨.
- 신규 외부 의존 없음(NFR-01). Phase 5 Goal(순수 인증 엔드포인트 3종 행위 증명) 달성.
