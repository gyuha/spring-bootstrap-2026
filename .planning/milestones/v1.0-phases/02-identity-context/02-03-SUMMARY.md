# Phase 2 — Wave 3 (테스트) Summary

**Completed:** 2026-05-31
**Wave:** 3 (FINAL) — 도메인 단위 테스트 + 통합 테스트, 전체 스위트 GREEN
**Status:** ✅ DONE

---

## 생성한 파일

| 파일 | 유형 | 검증 대상 |
|------|------|-----------|
| `src/test/java/com/anchors/baseline/identity/domain/model/UserTest.java` | 순수 단위 (Docker 불필요) | IDEN-02/03/06, disable 정상·멱등, Email 형식 검증(D-02) |
| `src/test/java/com/anchors/baseline/identity/UserLifecycleIT.java` | 통합 (`AbstractIntegrationTest` 상속, `@RecordApplicationEvents`) | IDEN-01/04/05, 스키마 정합(A4/A5) |

기존 main 코드(domain/application/infrastructure, V2 마이그레이션)는 Wave 1+2 산출물로 변경 없음.

---

## UserTest — 6개 @Test (Spring/Testcontainers 어노테이션 없음)

1. `linkIdentityTransitionsToActive` — IDEN-02: invite→linkIdentity("oid-1","홍길동") → ACTIVE, externalId/displayName 갱신
2. `linkIdentityTwiceIsRejected` — IDEN-03: 두 번째 linkIdentity → `InvalidStateTransition`
3. `linkIdentityPreservesInternalNoteAndUpdatesDisplayName` — IDEN-06: `updateInternalNote()` 후 linkIdentity → internalNote 불변, displayName 갱신
4. `disableTransitionsToDisabled` — disable() → DISABLED
5. `disableTwiceIsIdempotent` — Open Q1: disable() 두 번 → 예외 없이 DISABLED 유지 (무연산 멱등)
6. `emailValidatesFormatAtConstruction` — D-02: 잘못된 Email → `IllegalArgumentException`, 유효 입력 통과

## UserLifecycleIT — 4개 @Test (서비스 경유 구동)

1. `inviteCreatesInvitedUser` — IDEN-01(SC#1): invite → findById → INVITED, email 일치
2. `duplicateEmailIsRejected` — IDEN-05 UNIQUE(SC#1): 동일 email 재초대 → `EmailAlreadyExists`(선검사)
3. `disablePublishesUserDisabledEventAfterSave` — IDEN-04(SC#3): invite→linkIdentity→disable 후 `events.stream(UserDisabled.class)` 에 userId 1건, status=DISABLED. **이벤트는 service.disable() 내부 save() 시점 발행 — 호출 후 단언**(Pitfall 3)
4. `localPkRemainsBusinessReferenceAfterLinkIdentity` — IDEN-05(SC#5): linkIdentity 후에도 불변 로컬 PK(User.id)로 동일 사용자 조회, status=ACTIVE

`@Transactional` 은 테스트에 붙이지 않았다 — `IdentityApplicationService` 가 자기 트랜잭션 내에서 save() 를 실행해 이벤트를 발행하므로, 서비스 경유 구동이 이벤트 캡처의 신뢰 가능한 경로다.

---

## 전체 `./gradlew test` 결과

`./gradlew test --rerun-tasks` (Phase 1 lesson대로 독립 재검증) → **BUILD SUCCESSFUL**

| Test class | tests | skipped | failures | errors |
|------------|-------|---------|----------|--------|
| ArchitectureTest | 1 | 0 | 0 | 0 |
| UserTest | 6 | 0 | 0 | 0 |
| UserLifecycleIT | 4 | 0 | 0 | 0 |
| ActuatorHealthTest | 1 | 0 | 0 | 0 |
| FlywayMigrationTest | 2 | 0 | 0 | 0 |
| PersistenceIntegrationTest | 2 | 0 | 0 | 0 |
| VirtualThreadTest | 2 | 0 | 0 | 0 |
| **합계** | **18** | **0** | **0** | **0** |

ArchUnit `hexagonalLayerDependencies` 가 신규 identity 패키지에 자동 적용되어 **0 layer violations** 로 통과(포트=domain, 어댑터=infra, 예외 타입 배치 정합 — Pitfall 5 미발생).

---

## 검증된 가정 (RESEARCH Assumptions Log)

- **A4 (중간→확인됨):** `AbstractAggregateRoot.domainEvents` 가 `@Transient` 로 매핑 제외됨. IT 가 `ddl-auto: validate` 하에 부팅 성공 = Hibernate 매핑과 V2 스키마 정합. 스키마 충돌 없음.
- **A5 (중간→확인됨):** `ddl-auto: validate` + Flyway 권위. V2 DDL(`internal_note`/`display_name` 포함)과 User 엔티티 매핑 정확히 정합.
- **A7 / Pitfall 6 (중간→확인됨):** `existsByEmail(Email)` 파생 쿼리가 `@Embeddable` 임베디드 경로(value→email 컬럼)로 런타임 정상 해석. **@Query override 불필요.**
- **Open Q1 (해소):** disable 멱등 = 무연산(이미 DISABLED 시 이벤트 미발행). 단위 테스트로 확인.

---

## 편차 (Deviations)

**없음.** `existsByEmail` 에 대한 `@Query` override 추가 불필요 — 파생 쿼리가 그대로 동작. build.gradle.kts 에 하드코딩 api.version 미추가(로컬 `~/.gradle/gradle.properties` 의 `dockerApiVersion=1.43` 유지). 테스트 비활성화·main 코드 변경 없음.
