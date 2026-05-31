---
phase: 05
slug: bff-auth-session
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-31
---

# Phase 05 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + MockMvc + Testcontainers(postgres:16/redis:7) + WireMock(OIDC) |
| **Config file** | build.gradle (의존성 Phase 3에서 확립, 신규 추가 없음 — NFR-01) |
| **Quick run command** | `./gradlew test --tests "*AuthControllerIT" --tests "*BffAuthIT"` |
| **Full suite command** | `./gradlew test --rerun-tasks` |
| **Estimated runtime** | ~120 초 (Testcontainers 부팅 포함) |

> 라이브 구동(`task run`)은 로컬 포트 혼잡(8080/5432/6379 타 프로젝트 점유)으로 차단될 수 있음 — SC 단언은 전부 Testcontainers/MockMvc 기반(Phase 1~4 lesson).

---

## Sampling Rate

- **After every task commit:** Run quick run command
- **After every plan wave:** Run full suite command
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 120 초

---

## Per-Task Verification Map

> 플래너가 PLAN.md 작성 시 task별로 채운다. SC↔테스트 매핑은 RESEARCH.md "## Validation Architecture" 참조.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (planner fills) | | | AUTH-06/07/10 | | | IT/unit | `./gradlew test` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `BffAuthIT.logout()` 헬퍼의 `/logout` → `/api/auth/logout` 경로 갱신 (RESEARCH 발견 #5 — logoutUrl 변경 시 기존 테스트 회귀)
- [ ] `AuthControllerIT` 신설 — AUTH-06/07/10 SC 단언 스텁

*기존 Testcontainers/WireMock 인프라(AbstractIntegrationTest, BffAuthIT 형판)가 신규 프레임워크 설치 불필요를 커버.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| (none) | — | — | — |

*All phase behaviors have automated verification (MockMvc/Testcontainers/WireMock). 라이브 구동은 포트 혼잡으로 제외하되 서버측 세션 상태 read-back으로 대체 단언.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 120s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
