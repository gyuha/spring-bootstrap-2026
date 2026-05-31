---
status: complete
phase: 02-identity-context
source: [02-01-SUMMARY.md, 02-02-SUMMARY.md, 02-03-SUMMARY.md]
started: 2026-05-31T02:37:57Z
updated: 2026-05-31T02:37:57Z
---

## Current Test

[testing complete]

## Tests

### 1. invite → INVITED (IDEN-01)
expected: 관리자가 이메일로 사용자를 초대하면 INVITED 상태로 저장되고 email 이 일치한다.
result: pass
evidence: "UserLifecycleIT.inviteCreatesInvitedUser GREEN — 실 PostgreSQL(Testcontainers)에 저장 후 findById status=INVITED."

### 2. linkIdentity → ACTIVE + 멱등성 (IDEN-02/03)
expected: INVITED 사용자에 신원 연결 시 ACTIVE 전이, externalId/displayName 갱신. 재연결 시 예외.
result: pass
evidence: "UserTest.linkIdentityTransitionsToActive + linkIdentityTwiceIsRejected(InvalidStateTransition) GREEN."

### 3. disable → DISABLED + UserDisabled 발행 (IDEN-04)
expected: disable() 호출 시 DISABLED 전이, save() 후 UserDisabled(userId) 이벤트 1건 발행. 이미 DISABLED면 무연산.
result: pass
evidence: "UserLifecycleIT.disablePublishesUserDisabledEventAfterSave (@RecordApplicationEvents, save 후 단언) + UserTest.disableTwiceIsIdempotent GREEN."

### 4. email UNIQUE + 로컬 PK 매칭 (IDEN-05)
expected: 중복 email 거부(선검사 + DB UNIQUE 최종 방어), 매칭은 불변 로컬 PK(User.id).
result: pass
evidence: "UserLifecycleIT.duplicateEmailIsRejected(EmailAlreadyExists) + dbUniqueConstraintRejectsDuplicateEmail(DataIntegrityViolationException, 선검사 우회) + localPkRemainsBusinessReferenceAfterLinkIdentity GREEN."

### 5. 필드 소유권 — IdP 갱신·관리자 보존 (IDEN-06)
expected: linkIdentity 는 displayName(IdP)만 갱신하고 internalNote(관리자)는 보존(시그니처에 인자 없음).
result: pass
evidence: "UserTest.linkIdentityPreservesInternalNoteAndUpdatesDisplayName GREEN."

### 6. ArchUnit 계층 의존 — identity 패키지 (PLAT-02 게이트)
expected: identity 패키지가 interfaces→application→domain 의존 방향을 준수(application→infrastructure 위반 0).
result: pass
evidence: "ArchitectureTest GREEN — 신규 identity 패키지에 게이트 자동 적용, 위반 0."

### 7. Flyway V2 + 스키마 정합 (cold-start 대체)
expected: V2 마이그레이션이 users 테이블을 생성하고 Hibernate 매핑이 ddl-auto:validate 를 통과한다.
result: pass
evidence: "UserLifecycleIT 컨텍스트 부팅 성공 = Flyway V2 적용 + 매핑 정합(A4/A5) 실증. (라이브 cold-start 는 8080/5432/6379 타 프로젝트 점유로 미수행 — Testcontainers IT 가 동일 경로 검증.)"

### 8. 전체 테스트 스위트
expected: ./gradlew test 전체 GREEN.
result: pass
evidence: "19 tests, 0 failures, 0 errors (독립 재실행 --rerun-tasks 확인)."

## Summary

total: 8
passed: 8
issues: 0
pending: 0
skipped: 0

## Gaps

[none]
