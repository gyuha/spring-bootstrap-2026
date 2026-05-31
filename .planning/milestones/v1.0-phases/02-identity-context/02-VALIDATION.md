---
phase: 2
slug: identity-context
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-05-31
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. (02-RESEARCH.md §Validation Architecture 기반)

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (Jupiter) + AssertJ + Spring Boot Test + Testcontainers 1.21.3 |
| **Config file** | `gradle/libs.versions.toml` (의존 버전), `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` (싱글턴 컨테이너 베이스) |
| **Quick run command** | `./gradlew test --tests "*UserTest"` (도메인 단위 — Docker 불필요) |
| **Full suite command** | `./gradlew test` (ArchUnit + 통합 포함 전체 GREEN) |
| **Estimated runtime** | 단위 ~3s / 전체 ~15-30s (Testcontainers 컨테이너 재사용) |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew test --tests "*UserTest"` (단위 우선, 빠름)
- **After every plan wave:** Run `./gradlew test` (ArchUnit 포함 전체)
- **Before `/gsd:verify-work`:** Full suite must be green — Phase 1 lesson대로 `--rerun-tasks`로 독립 재검증
- **Max feedback latency:** ~30 seconds

---

## Per-Task Verification Map

> Task ID는 PLAN.md 생성 시 채워진다(현재 요구사항 수준 매핑). Threat Ref는 각 PLAN의 `<threat_model>`에서 확정.

| Requirement | Wave | Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|------|----------|-----------|-------------------|-------------|--------|
| IDEN-01 | int | `invite(email)` → INVITED 저장 + email UNIQUE | integration | `./gradlew test --tests "*UserLifecycleIT"` | ❌ W0 | ⬜ pending |
| IDEN-02 | unit+int | `linkIdentity` → INVITED→ACTIVE 전이 | unit + integration | `./gradlew test --tests "*UserTest"` | ❌ W0 | ⬜ pending |
| IDEN-03 | unit | 동일 신원 두 번 연결 → 예외 (멱등성 가드) | unit | `./gradlew test --tests "*UserTest"` | ❌ W0 | ⬜ pending |
| IDEN-04 | int | `disable()` → DISABLED + `UserDisabled` 발행 | integration (이벤트 단언) | `./gradlew test --tests "*UserLifecycleIT"` | ❌ W0 | ⬜ pending |
| IDEN-05 | int | email UNIQUE + 매칭=불변 로컬 PK(User.id) | integration (제약 단언) | `./gradlew test --tests "*UserLifecycleIT"` | ❌ W0 | ⬜ pending |
| IDEN-06 | unit | IdP 필드 갱신, 관리자 필드 보존 | unit (시그니처/동작) | `./gradlew test --tests "*UserTest"` | ❌ W0 | ⬜ pending |
| 계층 | arch | identity 패키지 의존 방향(포트=domain, 어댑터=infra) | arch | `./gradlew test --tests "ArchitectureTest"` | ✅ (기존 게이트 자동 적용) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/anchors/baseline/identity/domain/model/UserTest.java` — 도메인 단위(전이/멱등성/필드 소유권, IDEN-02/03/06). Testcontainers 불필요.
- [ ] `src/test/java/com/anchors/baseline/identity/.../UserLifecycleIT.java` — 통합(`AbstractIntegrationTest` 상속, IDEN-01/04/05 + `UserDisabled` 발행 단언, `@RecordApplicationEvents`).
- [ ] `src/main/resources/db/migration/V2__create_identity_users.sql` — 테스트가 의존하는 스키마(users 테이블).
- [ ] 프레임워크 추가: **없음** — JUnit5/AssertJ/Testcontainers 모두 Phase 1에 존재.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| *(없음)* | — | — | — |

All phase behaviors have automated verification.

---

## 핵심 검증 주의 (RESEARCH 기반)

- **이벤트 발행 타이밍:** `UserDisabled`는 `disable()` 직후가 아니라 `repository.save()` 실행 중·트랜잭션 내·커밋 전 동기 발행. 테스트는 `save()` 후에 단언. `@RecordApplicationEvents` + `ApplicationEvents` 주입.
- **A4 (중간):** `AbstractAggregateRoot.domainEvents`가 `@Transient`로 매핑 제외되는지 통합 테스트로 스키마 정합 검증.
- **A5 (중간):** Phase 1 `application.yml`은 `ddl-auto: validate` + Flyway 권위 — V2 DDL과 Hibernate 매핑 정확히 정합해야 함.
- **A6/Open Q2 (중간):** SC#4 관리자 필드가 Phase 2에 실재하지 않으면 시그니처 수준 단언으로 강등(planner 판단).

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter (플래닝 검증 후)

**Approval:** pending
