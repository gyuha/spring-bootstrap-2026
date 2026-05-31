---
phase: 06
slug: me-aggregation
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-05-31
---

# Phase 06 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + MockMvc + Testcontainers(postgres:16/redis:7) + WireMock(OIDC) |
| **Config file** | build.gradle (의존성 Phase 3에서 확립, 신규 추가 없음 — NFR-01) |
| **Quick run command** | `./gradlew test --tests "*BffAuthMeIT" --tests "*ArchitectureTest"` |
| **Full suite command** | `./gradlew test --rerun-tasks` |
| **Estimated runtime** | ~120 초 (Testcontainers 부팅 포함) |

> 라이브 구동 금지(포트 혼잡 8080/5432/6379). 권한 집계는 실 Postgres(직접 부여 grant 시드)에서 단언. oidcLogin 우회 금지 — principal user_id ↔ User.id ↔ grant.userId 3자 일치.

---

## Sampling Rate

- **After every task commit:** Run quick run command
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 120 초

---

## Per-Task Verification Map

> 플래너가 PLAN.md 작성 시 task별로 채운다. SC↔테스트 매핑은 RESEARCH.md "## Validation Architecture" 참조.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|--------|
| (planner fills) | | | AUTH-08/09/11 | info disclosure | me() 인증 필수(401), 민감정보 미포함 | IT/unit | `./gradlew test` | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `BffAuthMeIT` 신설 — AUTH-08/09 SC#1·2 단언 스텁 (Identity 신원 + Authorization 권한 집계)
- [ ] ArchUnit GREEN 단언(AUTH-11 SC#3 — 무변경 통과 확인)

*기존 Testcontainers/WireMock 인프라(AbstractIntegrationTest, BffAuthSessionIT 형판)가 신규 프레임워크 설치 불필요를 커버.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| (none) | — | — | — |

*All phase behaviors have automated verification (MockMvc/Testcontainers/WireMock).*

---

## Validation Sign-Off

- [x] All tasks have `<automated>` verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-05-31
