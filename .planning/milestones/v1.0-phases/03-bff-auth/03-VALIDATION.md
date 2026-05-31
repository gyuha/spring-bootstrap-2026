---
phase: 3
slug: bff-auth
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-31
---

# Phase 3 — Validation Strategy

> Per-phase validation contract. (03-RESEARCH.md §Validation Architecture 기반)

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + spring-security-test 6.5.1 + Testcontainers 1.21.3 (+ WireMock for OIDC token-exchange) |
| **Config file** | `gradle/libs.versions.toml` (deps), `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` (싱글턴 PostgreSQL+Redis 베이스) |
| **Quick run command** | `./gradlew test --tests "*ArchitectureTest*"` (계층 게이트 — auth 컨텍스트 위반 즉시 RED) |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | 단위 ~3s / 전체 ~30-60s (WireMock + Testcontainers) |

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests "*ArchitectureTest*"` (계층 위반 즉시 RED — lesson 01 P1)
- **After every plan wave:** `./gradlew test --tests "*BffAuth*" --tests "*ArchitectureTest*"`
- **Before `/gsd:verify-work`:** `./gradlew test` 전체 GREEN — `--rerun-tasks` 독립 재검증(lesson)
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| Requirement | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|-----------|-------------------|-------------|--------|
| AUTH-01 | OIDC 로그인 흐름 인증 성공 | integration (WireMock IdP) | `./gradlew test --tests "*BffAuthIT*"` | ❌ W0 | ⬜ pending |
| AUTH-02/SC#1 | 응답 쿠키에 SESSION만·토큰 부재 + Redis 세션에 토큰 존재 | integration (WireMock + Redis TC) | `*BffAuthIT.tokenOnlyInRedisNotInCookie*` | ❌ W0 | ⬜ pending |
| AUTH-03/SC#2 | 쿠키 세션만으로 인증 API 200 | integration (oidcLogin) | `*BffAuthIT.cookieSessionAuthenticatesApi*` | ❌ W0 | ⬜ pending |
| AUTH-04/SC#4 | 로그아웃 후 동일 쿠키 재요청 401/redirect | integration | `*BffAuthIT.logoutInvalidatesSession*` | ❌ W0 | ⬜ pending |
| AUTH-05/SC#3 | 최초 로그인 시 linkIdentity → INVITED→ACTIVE | integration (Postgres TC) | `*BffAuthIT.firstLoginLinksIdentity*` | ❌ W0 | ⬜ pending |
| AUTH-05 (멱등) | 재로그인 시 linkIdentity 미호출(이미 ACTIVE) | integration | `*BffAuthIT.reLoginIsIdempotent*` | ❌ W0 | ⬜ pending |
| 계층 | auth 컨텍스트 4계층 의존 위반 0 | arch | `./gradlew test --tests "*ArchitectureTest*"` | ✅ (기존, 자동 적용) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `auth/.../BffAuthIT.java` — SC#1~4·AUTH-01~05 행위 단언, `AbstractIntegrationTest` 상속
- [ ] WireMock OIDC 스텁 fixture(discovery/token/jwks) — SC#1·SC#4 토큰 교환 흐름용 (test 스코프)
- [ ] `findByEmail(Email)` 파생 쿼리 해석 슬라이스/통합 테스트 (Pitfall 6)
- [ ] `BaselineOidcUser` Serializable 라운드트립 단언 (Pitfall 2) — Redis 세션 직렬화
- [ ] 직렬화 라운드트립 가드 — 로그인 후 Redis 세션 직렬화/역직렬화 성공 (Pitfall 1)
- [ ] 프레임워크 추가: spring-security-test, WireMock(test) — 신규 test 의존

---

## 핵심 검증 주의 (RESEARCH 기반)

- **ArchUnit 동일 레이어:** `auth.application → identity.application`은 같은 "Application" 레이어라 게이트 통과(ArchUnit 소스로 실증). 계층 위반 0 유지를 매 커밋 확인.
- **Spring Session 직렬화:** **JDK 기본 유지**(JSON 전환 금지 — OAuth2AuthorizedClient/OidcUser 역직렬화 깨짐). `BaselineOidcUser`는 Serializable 필수.
- **토큰 Redis-only:** `HttpSessionOAuth2AuthorizedClientRepository`(프레임워크 기본) — 커스텀 인프라 금지(NFR-01). SC#1은 쿠키에 SESSION만 + Redis 세션에 토큰 존재로 단언.
- **로그아웃(SC#4):** `invalidateHttpSession(true)` + `deleteCookies` → Redis 세션 삭제. 사후 동일 쿠키 401/redirect 단언.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 실제 IdP 연동 (Google/Kakao 등) | AUTH-01 | 실 IdP 자격증명·브라우저 필요, 로컬 포트 혼잡 | 운영/스테이징에서 수동 — 베이스라인은 WireMock/oidcLogin으로 자동 검증 |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter (플래닝 검증 후)

**Approval:** pending
