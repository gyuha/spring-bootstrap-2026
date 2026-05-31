---
phase: 1
slug: platform-skeleton
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-05-30
---

# Phase 1 — Validation Strategy

> 실행 중 피드백 샘플링을 위한 단계별 검증 계약. RESEARCH.md "검증 아키텍처" 섹션 기반.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (spring-boot-starter-test 포함) + ArchUnit + Testcontainers |
| **Config file** | `build.gradle.kts` (`tasks.test { useJUnitPlatform() }`) |
| **Quick run command** | `./gradlew test --tests "com.anchors.baseline.architecture.*"` |
| **Full suite command** | `./gradlew test` |
| **Estimated runtime** | ~60–120초 (Testcontainers 컨테이너 기동 포함) |

---

## Sampling Rate

- **After every task commit:** `./gradlew test --tests "com.anchors.baseline.architecture.*"` (ArchUnit — 빠름, 컨테이너 불필요)
- **After every plan wave:** `./gradlew test` (Testcontainers 통합 포함 전체)
- **Before `/gsd:verify-work`:** 전체 스위트 green 필수
- **Max feedback latency:** ~120초

---

## Per-Task Verification Map

> 계획 수립 전 작성된 검증 계약이므로 Task ID는 요구사항 단위로 매핑한다. Planner는 각 PLAT-XX를 커버하는 task에 아래 자동화 명령을 `<automated>` verify로 연결해야 한다.

| Requirement | Behavior | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|-------------|----------|------------|-----------------|-----------|-------------------|-------------|--------|
| PLAT-01 | 가상 스레드 활성화 + 기동 로그 확인 | — | N/A | 통합/smoke | `./gradlew test --tests "*VirtualThreadTest*"` | ❌ W0 | ⬜ pending |
| PLAT-02 | 패키지 의존 방향 위반 없음 | — | N/A | 아키텍처(ArchUnit) | `./gradlew test --tests "*ArchitectureTest*"` | ❌ W0 | ⬜ pending |
| PLAT-03 | Flyway 마이그레이션 실행 + `flyway_schema_history` 기록 | — | N/A | 통합(Testcontainers) | `./gradlew test --tests "*FlywayMigrationTest*"` | ❌ W0 | ⬜ pending |
| PLAT-04 | JPA 쓰기 + MyBatis 조회 단일 트랜잭션 원자성 | — | N/A | 통합(Testcontainers) | `./gradlew test --tests "*PersistenceIntegrationTest*"` | ❌ W0 | ⬜ pending |
| PLAT-05 | Redis 연결 확인 | T-1: Redis 자격증명 로컬 한정 | Redis는 로컬 Docker만, 운영 보안은 Phase 3 BFF에서 | 통합(Testcontainers) | `./gradlew test --tests "*PersistenceIntegrationTest*"` | ❌ W0 | ⬜ pending |
| PLAT-06 | `/actuator/health`가 app+db+redis UP 반환 | T-2: Actuator 정보 노출 | `show-details: always`는 dev 전용, 운영은 `when_authorized` | 통합(Testcontainers) | `./gradlew test --tests "*ActuatorHealthTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `src/test/java/com/anchors/baseline/architecture/ArchitectureTest.java` — PLAT-02 (ArchUnit `layeredArchitecture()`)
- [ ] `src/test/java/com/anchors/baseline/platform/infrastructure/PersistenceIntegrationTest.java` — PLAT-04·05 (JPA+MyBatis 단일 트랜잭션, `em.flush()` 포함)
- [ ] `src/test/java/com/anchors/baseline/platform/infrastructure/ActuatorHealthTest.java` — PLAT-06
- [ ] `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` — 공통 base (`@Testcontainers` + `@ServiceConnection` Postgres/Redis 공유)
- [ ] `src/test/resources/application-test.yml` — Testcontainers 전용 프로파일
- [ ] `*VirtualThreadTest*`, `*FlywayMigrationTest*` — PLAT-01·03 검증 스텁

*JUnit 5는 spring-boot-starter-test에 포함 — 별도 프레임워크 설치 불필요. ArchUnit/Testcontainers 의존성은 build.gradle.kts에 추가 필요(Wave 0).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| 기동 로그에 가상 스레드 활성 육안 확인 | PLAT-01 | 로그 메시지는 자동 assert(`Thread.currentThread().isVirtual()`)로 대체 가능하나, 실제 기동 로그 확인은 보완적 | `./gradlew bootRun` 후 로그에서 virtual thread 관련 라인 확인 |

*핵심 동작(PLAT-01의 `isVirtual()` assert 포함)은 모두 자동 검증으로 커버됨.*

---

## Validation Sign-Off

- [ ] 모든 task가 `<automated>` verify 또는 Wave 0 의존성을 가짐
- [ ] 샘플링 연속성: 자동 verify 없는 task가 3개 연속 금지
- [ ] Wave 0가 모든 MISSING 참조를 커버
- [ ] watch-mode 플래그 없음
- [ ] 피드백 지연 < 120초
- [x] `nyquist_compliant: true` frontmatter 설정 (계획 검증 통과 — plan-checker)

**Approval:** approved 2026-05-30 (plan-checker 검증 통과; `wave_0_complete`는 실행 단계에서 갱신)
