---
phase: 4
slug: authorization
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-31
---

# Phase 4 — Validation Strategy

> Per-phase validation contract. (04-RESEARCH.md §Validation Architecture 기반)

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (postgres:16, redis:7) — 재귀 CTE는 실 Postgres 필수(H2 금지) |
| **Config file** | src/test/resources/application-test.yml |
| **Base class** | com.anchors.baseline.AbstractIntegrationTest (싱글턴 Testcontainers, RANDOM_PORT, test 프로파일) — 상속 필수 |
| **Quick run command** | `./gradlew test --tests "*Authorization*" --tests "*Permission*"` |
| **Full suite command** | `./gradlew test --rerun-tasks` |

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests "*Authorization*" --tests "*Permission*" --tests "*ArchitectureTest*"`
- **After every plan wave:** `./gradlew test`
- **Before `/gsd:verify-work`:** 전체 GREEN + ArchitectureTest GREEN (신규 패키지 계층 정합 — lesson 01 P1)
- **Max feedback latency:** ~60 seconds

---

## Per-Task Verification Map

| SC / Req | Behavior | Test Type | Automated Command | File Exists | Status |
|----------|----------|-----------|-------------------|-------------|--------|
| SC#1 / AUTHZ-01 | grant→evaluate=true, revoke→evaluate=false 연속(캐시 없음, 즉시) | integration | `*PermissionEvaluationIT*` | ❌ W0 | ⬜ pending |
| SC#2 / AUTHZ-06 | evaluate 진리표: 직접/그룹/상속/조합/없음 + 역할함의(EDITOR⊇VIEW) | integration (@ParameterizedTest) | `*PermissionEvaluationIT*` | ❌ W0 | ⬜ pending |
| SC#3 / AUTHZ-05·07 | 상위 부여→하위 ListObjects 포함(재귀 CTE) | integration | `*ListObjectsIT*` | ❌ W0 | ⬜ pending |
| SC#4 / AUTHZ-09 | INVITED userId grant→DB 저장→linkIdentity ACTIVE→동일 userId evaluate 반영 | integration (Identity+Authz) | `*InvitedGrantSurfacingIT*` | ❌ W0 | ⬜ pending |
| SC#5 / AUTHZ-08 | AuthorizationPort 존재 + Postgres 어댑터 + 포트만 의존(교체 가능) | structural + 주입 | `*ArchitectureTest*` + `*AuthorizationPortWiringIT*` | ❌ W0 (Arch ✅) | ⬜ pending |
| 게이트 | authorization 4계층 정합 | ArchUnit | `./gradlew test --tests "*ArchitectureTest*"` | ✅ 자동 적용 | ⬜ pending |
| 사이클 가드 | resource_hierarchy 사이클 입력 시 무한 루프 없음(CYCLE/UNION) | integration | `*ResourceHierarchyCycleIT*` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `PermissionEvaluationIT.java` — SC#1·#2 (grant/revoke 즉시성 + 진리표), AbstractIntegrationTest 상속
- [ ] `ListObjectsIT.java` — SC#3 (계층 상속 + ListObjects)
- [ ] `InvitedGrantSurfacingIT.java` — SC#4 (실제 IdentityApplicationService.linkIdentity 호출 + grant/evaluate 엮음 — placeholder 금지)
- [ ] `ResourceHierarchyCycleIT.java` — 사이클 가드(실 Postgres에 사이클 일부러 입력해 무한 루프 부재 증명)
- [ ] `AuthorizationPortWiringIT.java` — SC#5 포트/어댑터 주입 교체 가능 구조
- [ ] (단위) `PermissionEvaluatorTest.java` — 역할 함의(EDITOR⊇VIEW) in-memory fake 읽기 포트로 단위 검증
- [ ] 프레임워크 추가: **없음** (MyBatis/JPA/Postgres/Testcontainers/Security 모두 존재)

---

## 핵심 검증 주의 (RESEARCH 기반)

- **Spring Security PermissionEvaluator 메서드는 `hasPermission(...)` (NOT `evaluate`)** — javap 실측. 도메인 서비스는 순수 `evaluate(userId,resource,action)`. 선택적 Spring 어댑터만 hasPermission 구현(SC 비필수).
- **ArchUnit Open Q1**: PermissionEvaluator/읽기 포트는 `authorization.application`에 (domain은 application 포트 주입 불가). 신규 패키지 추가 후 ArchitectureTest 실증 필수.
- **재귀 CTE**: 조상 방향만 전개(상속 오버리치 방지), CYCLE/UNION으로 사이클 가드, MyBatis `#{}` 파라미터만(SQL injection 방지 — `${}` 금지). 실 Postgres Testcontainer.
- **SC#4 soft reference**: authorization은 userId(Long)만 참조, identity users로 FK 없음 → ACTIVE 전이가 authorization 데이터 미접촉, 추가 코드 없이 충족. 단 테스트는 실제 linkIdentity 호출로 단언.
- **created_at**: 신규 엔티티는 단일 소스로(D-09 — 베이스라인 dual-source 패턴 반복 회피).

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| *(없음)* | — | — | — |

All phase behaviors have automated verification.

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter (플래닝 검증 후)

**Approval:** pending
