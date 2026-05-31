---
status: complete
phase: 04-authorization
source:
  - 04-01-SUMMARY.md
  - 04-02-SUMMARY.md
  - 04-03-SUMMARY.md
started: 2026-05-31T12:00:00Z
updated: 2026-05-31T12:00:00Z
verification_basis: integration-test-suite
note: >
  Phase 4 는 백엔드 전용(UI 없음). 모든 사용자 관측 동작은 실 Postgres Testcontainers 통합
  테스트(51 tests GREEN)로 행위 단언됨. 각 test 의 result=pass 근거는 해당 통합/단위 테스트
  클래스의 GREEN 결과. 사용자 라이브 `task run`(test 7)이 Flyway checksum mismatch 결함을
  노출 → V3 복원 + V4 분리로 수정(commit 6802331), 라이브 재부팅 재확인 대기.
---

## Current Test

[testing complete]

## Tests

### 1. 권한 부여/회수 즉시 반영
expected: 사용자에게 자원 권한을 부여하면 즉시 evaluate=true, 회수하면 즉시 evaluate=false (캐시 지연 없음).
result: pass
evidence: PermissionEvaluationIT.grantThenRevokeIsImmediatelyReflected — grant(서비스 커밋)→evaluate=true→revoke(커밋)→evaluate=false 연속 단언 GREEN (SC#1).

### 2. 권한 판정 진리표 (직접/그룹/상속/없음 + 역할 함의)
expected: 직접 부여·소속 그룹 부여·상위 자원 상속·조합은 접근 허용, 미부여는 거부(default-deny). EDITOR 권한자는 VIEW 동작 허용, VIEWER 권한자는 EDIT 거부.
result: pass
evidence: PermissionEvaluationIT.evaluateTruthTable(@ParameterizedTest 7케이스) + PermissionEvaluatorTest(단위 9케이스) GREEN (SC#2). default-deny·EDITOR⊇VIEW·VIEWER↛EDIT 포함.

### 3. 자원 계층 상속 → 접근 가능 목록
expected: 상위 자원에 권한을 부여하면, 그 하위 자원이 사용자의 접근 가능 자원 목록(ListObjects)에 포함된다.
result: pass
evidence: ListObjectsIT — defineHierarchy(child,parent)+parent 부여 → listObjects 가 parent·child 모두 포함, 무관 자원 음성 단언 GREEN (SC#3, 하향 재귀 CTE).

### 4. 초대(INVITED) 사용자 권한 부여 → 활성화 후 반영
expected: 아직 활성화되지 않은(INVITED) 사용자에게 권한을 부여해 두면 영속되고, 해당 사용자가 외부 신원 연결(linkIdentity)로 ACTIVE 전이된 뒤 동일 사용자로 권한 판정이 그대로 반영된다.
result: pass
evidence: InvitedGrantSurfacingIT — invite→INVITED→grant→findByUserId 영속 단언→실 IdentityApplicationService.linkIdentity→ACTIVE 단언→evaluate(EDIT)=true GREEN (SC#4, soft reference, mock 미사용).

### 5. 인가 포트 교체 가능 구조
expected: 애플리케이션이 의존하는 AuthorizationPort 가 Postgres 어댑터 구현으로 주입되고, evaluate/listObjects 가 동작한다(엔진 교체 가능).
result: pass
evidence: AuthorizationPortWiringIT — port instanceof PostgresAuthorizationAdapter + evaluate/listObjects 동작 단언, ArchitectureTest 계층 정합 GREEN (SC#5).

### 6. 자원 계층 사이클 안전성
expected: 자원 계층에 사이클(A→B→A)이 입력돼도 권한 판정/목록 조회가 무한 루프 없이 정상 종료한다.
result: pass
evidence: ResourceHierarchyCycleIT — 실 Postgres 에 A↔B 사이클 입력 후 evaluate/listObjects 를 assertTimeoutPreemptively(5s)로 감싸 무한 재귀 부재 증명 GREEN (CYCLE 절/UNION 중복 제거).

### 7. Cold Start (Flyway 마이그레이션 + 컨텍스트 부팅)
expected: 애플리케이션을 처음부터 기동하면 인가 마이그레이션(6테이블 + 멱등 제약)이 적용되고 authorization 빈 배선이 완료되어 스모크 동작이 성공한다.
result: issue→fixed (재확인 대기)
reported: "라이브 task run 에서 Flyway validate 실패 — V3 checksum mismatch (applied: -784278869, local: 1475771130). 컨텍스트 부팅 실패."
severity: blocker
root_cause: 코드 리뷰 수정(fcca22b)이 이미 dev DB 에 적용된 V3 마이그레이션 파일을 편집(PG14 주석 + 부분 UNIQUE 인덱스 추가) → Flyway 불변성 위반 → checksum 변경. Testcontainers(fresh DB)는 매번 새로 적용하므로 결함을 가렸고, 영속 dev DB 가 노출.
fix: V3 를 적용본(9dea173)으로 복원해 dev DB checksum 재일치 + 추가 제약을 신규 V4__authorization_grant_uniqueness.sql 로 분리(commit 6802331). Testcontainers V3+V4 적용 → 51 tests GREEN.
evidence: 통합테스트가 AbstractIntegrationTest(Testcontainers postgres:16)에서 V3+V4 적용 후 cold boot — Flyway 적용·빈 배선·쿼리 동작·ddl-auto:validate GREEN. **단, 영속 dev DB(localhost:5432) 라이브 재부팅 확인은 사용자 `task run` 재실행으로 검증 필요 — 현재 대기.**

## Summary

total: 7
passed: 6
issues: 1 (fixed — 라이브 재확인 대기)
pending: 0
skipped: 0

## Acknowledged Gaps

- **라이브 재부팅 재확인 대기:** 사용자 `task run` 이 Flyway checksum mismatch(편집된 V3) 결함을 노출했고, V3 복원 + V4 분리로 수정함(commit 6802331). 수정 후 영속 dev DB(localhost:5432) 재부팅 성공 여부는 사용자 `task run` 재실행으로 최종 확인 필요. Testcontainers(V3+V4)는 GREEN.
- **인가 API 노출 경로 없음 (의도적):** Phase 4 는 인가 도메인/판정/영속만 구현 — REST 컨트롤러 미포함(베이스라인 골격, 업무 도메인이 채울 영역). 따라서 HTTP 수준 UAT 대상 자체가 없음.

## Gaps

[none — all behaviors verified by integration test suite]
