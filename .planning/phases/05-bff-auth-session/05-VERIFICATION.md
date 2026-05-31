---
phase: 05
slug: bff-auth-session
status: pass
verified: 2026-05-31
method: automated
---

# Phase 05 — Verification

> 검증 방식: 자동화(Testcontainers + WireMock + MockMvc). 라이브 구동은 포트 혼잡으로 제외, 서버측 세션
> 상태 read-back으로 대체 단언. 대화형 UAT 미수행 — 자동 검증 근거로 ship 승인(사용자 결정).

## Success Criteria 검증

| SC | 내용 | 검증 | 상태 |
|----|------|------|------|
| SC#1 | GET /api/auth/session 비인증 200 {authenticated:false}, 인증 시 userId | BffAuthSessionIT.sessionUnauthenticatedReturns200(), sessionAuthenticatedReturnsUserId() | ✅ pass |
| SC#2 | POST /api/auth/logout → 204, 세션 무효화·Redis 삭제, 이후 401 | BffAuthSessionIT.logoutReturns204AndInvalidatesSession() (sessionRepository.findById==null) | ✅ pass |
| SC#3 | GET /api/auth/login → OIDC 302 + returnTo, open-redirect 거부 | loginRedirectsToOAuthEndpoint(), loginWithReturnToRedirectsAfterLogin(), loginRejectsAbsoluteReturnTo() | ✅ pass |
| SC#4 | /api/auth/session·login permitAll, /api/** 401 불변 | permitAllMatchers() | ✅ pass |

## 증거

- `./gradlew test --rerun-tasks` → 64 tests GREEN(전체). BffAuthSessionIT 7/7, BffAuthIT 회귀 없음, ArchitectureTest GREEN.
- plan-checker 12/12 PASS, 코드 리뷰 C1(open-redirect backslash)·I1·I2·M2·M3 반영(d001d1b).

## Acknowledged Gaps

- 라이브 부팅 재확인 미수행(포트 혼잡). Testcontainers/WireMock가 실 토큰 교환·세션 검증을 대체.
