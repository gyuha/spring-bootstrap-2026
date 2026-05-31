---
status: pass
phase: 03-bff-auth
verified: 2026-05-31T08:12:28Z
method: automated-tests (WireMock OIDC + Testcontainers integration) + code-review
---

# Phase 3 Verification — BFF 인증

**Status: PASS** — 4개 성공 기준(SC#1~4) / 요구사항 AUTH-01~05 모두 자동화 테스트로 검증됨.

## Success Criteria

| SC | 요구사항 | 검증 | 증거 |
|----|----------|------|------|
| SC#1 | AUTH-02 토큰 Redis 서버세션 only·쿠키엔 SESSION만 (NFR-02) | ✅ | BffAuthIT.tokenOnlyInRedisNotInCookie (WireMock, 세션 attribute read-back) |
| SC#2 | AUTH-03 쿠키 세션만으로 인증 API | ✅ | BffAuthIT.cookieSessionAuthenticatesApi (/api/me 200, userId=로컬 User.id) |
| SC#3 | AUTH-05 최초 로그인 linkIdentity INVITED→ACTIVE + 멱등 | ✅ | BffAuthIT.firstLoginLinksIdentity + reLoginIsIdempotent |
| SC#4 | AUTH-04 로그아웃 시 Redis 세션 무효화·동일 쿠키 인증 실패 | ✅ | BffAuthIT.logoutInvalidatesSession (WireMock 세션 라운드트립, 401 + findById null) |
| — | AUTH-01 OIDC 로그인 흐름 | ✅ | BffAuthIT.oidcLoginFlowSucceeds (WireMock jwks 서명 + nonce) |

## Automated Verification

`./gradlew test --rerun-tasks` → **BUILD SUCCESSFUL, 28 tests, 0 failures, 0 errors** (독립 재실행).
- auth.BffAuthIT: 7 (SC#1~4 + 미초대 거부 + 멱등 — WireMock OIDC + Testcontainers Redis/Postgres)
- auth.BaselineOidcUserSerializationTest: 1 (Pitfall 2 직렬화 가드)
- architecture.ArchitectureTest: 1 (auth 패키지 계층 0 위반)
- Phase 1·2 기존: 19

## NFR-02 핵심 교정 (검증됨)

`HttpSessionOAuth2AuthorizedClientRepository` 빈을 명시 배선 — Spring Boot 기본은 `InMemoryOAuth2AuthorizedClientService`(토큰 앱 메모리)라 NFR-02 위반이었음. SC#1 테스트(세션 attribute read-back)가 적발. 리뷰가 바이트코드로 정당성 확인.

## Code Review

`sg-review`(superpowers:requesting-code-review) 수행. 판정 "With fixes", Critical 0. Important 2건(MeController null-tolerant, oauth2Login 스킵 WARN) 수정·커밋(3520434). 두 deviation(HttpSession 빈, CsrfCookieFilter)을 바이트코드 수준에서 검증. ArchUnit 0 위반.

## Acknowledged Gaps

- 라이브 app/실 IdP 미수행 — 포트 혼잡(8080/5432/6379 타 프로젝트) + 실 IdP 자격증명 부재. WireMock OIDC + Testcontainers가 토큰 교환·세션·로그아웃 전 경로를 실증.
- 조건부 oauth2Login: 운영 배포 시 OIDC registration 누락하면 로그인 불가(잠김) — 시작 WARN으로 fail-loud화. 실 IdP 연동 시 재확인.
- 리프레시 토큰 자동 갱신·CORS·실 IdP registration — 후속 phase(다운스트림 리소스 호출) 소관.
