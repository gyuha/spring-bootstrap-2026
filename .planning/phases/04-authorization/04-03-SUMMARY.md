# 04-03 SUMMARY — Authorization 검증 Wave (테스트 전용)

Phase 04(Authorization 컨텍스트)의 마지막 Wave이자 프로젝트 마지막 Wave. 목표는 SC#1~5 + 사이클 가드를
**실 Postgres(Testcontainers)** 위에서 행위로 단언하고 전체 `./gradlew test`를 GREEN으로 만드는 것이었다.

## 결과 요약

- **전체 스위트: BUILD SUCCESSFUL — 51 tests, 0 failures, 0 errors, 0 skipped** (`./gradlew test --rerun-tasks`, 5개 태스크 전부 재실행 — 캐시 미사용 독립 재검증).
- ArchitectureTest 포함 전 클래스 GREEN.
- **Wave-2 소스 수정 0건** — CTE SQL / MyBatis 매핑 / 엔티티-DDL 정합이 이미 일치해, 테스트가 첫 실행부터 GREEN. ddl-auto validate, 재귀 CTE(CYCLE/UNION 가드), source 라벨링 모두 손댈 필요 없었음.

신규 authorization 테스트 클래스별 카운트:

| 클래스 | tests | 검증 대상 |
|--------|-------|-----------|
| PermissionEvaluatorTest | 9 | 단위(POJO) — default-deny + 역할 함의 진리표 |
| PermissionEvaluationIT | 8 | SC#1 즉시성 + SC#2 진리표(직접/그룹/상속/조합/없음 + EDITOR→VIEW) |
| ListObjectsIT | 2 | SC#3 — 상위 부여→하위 ListObjects 포함(하향 재귀 CTE) |
| ResourceHierarchyCycleIT | 1 | 사이클 가드 — A↔B 사이클에서 5초 내 정상 종료 |
| InvitedGrantSurfacingIT | 1 | SC#4 — INVITED grant→영속 단언→실 linkIdentity ACTIVE→evaluate=true |
| AuthorizationPortWiringIT | 2 | SC#5 — port instanceof PostgresAuthorizationAdapter + evaluate/listObjects 동작 |

## 생성 파일 (src/test, 6개)

- `src/test/java/com/anchors/baseline/authorization/PermissionEvaluatorTest.java`
- `src/test/java/com/anchors/baseline/authorization/PermissionEvaluationIT.java`
- `src/test/java/com/anchors/baseline/authorization/ListObjectsIT.java`
- `src/test/java/com/anchors/baseline/authorization/ResourceHierarchyCycleIT.java`
- `src/test/java/com/anchors/baseline/authorization/InvitedGrantSurfacingIT.java`
- `src/test/java/com/anchors/baseline/authorization/AuthorizationPortWiringIT.java`

## SC 충족 매핑

- **SC#1 (즉시성):** PermissionEvaluationIT.grantThenRevokeIsImmediatelyReflected — grant(서비스 커밋)→evaluate=true→revoke(커밋)→evaluate=false 연속 단언. Pitfall 3 회피(grant는 서비스 호출로 커밋된 뒤 별 호출 evaluate가 읽음).
- **SC#2 (진리표):** PermissionEvaluationIT.evaluateTruthTable @ParameterizedTest 7케이스 — direct∪group∪inherited + default-deny + 역할 함의(EDITOR+VIEW=true, VIEWER+EDIT=false). 단위 진리표는 PermissionEvaluatorTest가 보강.
- **SC#3 (계층→ListObjects):** ListObjectsIT — defineHierarchy(child, parent) + parent 부여 → listObjects가 parent와 child 모두 포함. 무관 리소스 음성 단언 포함.
- **SC#4 (INVITED→ACTIVE):** InvitedGrantSurfacingIT — invite→INVITED userId→grant→`findByUserId`로 영속 단언→**실 `IdentityApplicationService.linkIdentity`**→ACTIVE 전이 단언→동일 userId evaluate(EDIT)=true. mock/placeholder 미사용(VALIDATION 명령 준수).
- **SC#5 (포트=Postgres 어댑터):** AuthorizationPortWiringIT — `assertThat(port).isInstanceOf(PostgresAuthorizationAdapter.class)` + evaluate/listObjects 동작. ArchitectureTest가 계층 정합 보강.
- **사이클 가드:** ResourceHierarchyCycleIT — A.parent=B, B.parent=A 사이클(UNIQUE(resource_id)라 서로 다른 행이므로 둘 다 INSERT 성공) 입력 후 evaluate/listObjects를 `assertTimeoutPreemptively(5s)`로 감싸 무한 재귀 부재 증명. 실 Postgres에서만 재현(CYCLE 절 / UNION 중복 제거).

## 구현 노트 / 결정

- **격리 전략:** `@SpringBootTest`는 기본 롤백하지 않으므로(그리고 Pitfall 3대로 커밋이 필요하므로), 각 IT는 `AtomicLong` 시퀀스 기반 고유 userId/groupId/resourceId로 격리. 클래스별 시퀀스 베이스를 분리(1M/2M/3M/4M/5M)해 교차 간섭 차단.
- **PermissionEvaluatorTest는 순수 POJO** — `new PermissionEvaluator(fakePort)` 생성자 주입, in-memory `PermissionReadPort` 익명 구현. Spring/Docker 미기동(1초 실행).
- **상속 케이스 검증 경로:** findEffectiveGrants의 ancestry CTE는 자기+부모 체인을 따라가므로, child evaluate 시 parent 부여가 `inherited`로 합산된다. ListObjects의 descendants CTE는 반대로 parent→child 하향 전개. 양방향 모두 실 데이터로 GREEN.

## 검증 명령 / 증거

```
./gradlew test --tests "*PermissionEvaluatorTest*"   → BUILD SUCCESSFUL (5 tasks)
./gradlew test --tests "*PermissionEvaluationIT*" --tests "*ListObjectsIT*"   → BUILD SUCCESSFUL
./gradlew test --tests "*ResourceHierarchyCycleIT*" --tests "*InvitedGrantSurfacingIT*" \
              --tests "*AuthorizationPortWiringIT*" --tests "*ArchitectureTest*"   → BUILD SUCCESSFUL
./gradlew test --rerun-tasks   → BUILD SUCCESSFUL (5 actionable tasks: 5 executed)

집계(build/test-results/test/*.xml): tests=51 failures=0 errors=0 skipped=0
```

## 편차

없음. Wave-2 소스 수정 불필요, 모든 SC + 사이클 가드 GREEN, 전체 스위트 GREEN.
