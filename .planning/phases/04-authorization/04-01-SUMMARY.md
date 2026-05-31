# Phase 04 Wave 1 — Authorization 도메인 + 계약 + Flyway V3 (SUMMARY)

**Completed:** 2026-05-31
**Scope:** Wave 1 of 3 — domain + application contracts + Flyway V3 only.
Wave 2(adapters/CTE) + Wave 3(tests)는 미구현(의도적).

## 생성 파일

### TASK 1 — Flyway V3
- `src/main/resources/db/migration/V3__create_authorization.sql`
  - 6 테이블: `global_role_grants`(AUTHZ-01), `menu_grants`(AUTHZ-02), `resource_grants`(AUTHZ-03/06),
    `groups`(AUTHZ-04), `group_members`(AUTHZ-04), `resource_hierarchy`(AUTHZ-05).
  - 전부 `BIGSERIAL PRIMARY KEY`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL` (DDL 기본값 절 생략 — D-09 단일 소스).
  - `menu_grants`/`resource_grants`: `CHECK((user_id IS NOT NULL) <> (group_id IS NOT NULL))` XOR.
  - 물리 FK 없음(soft reference §4.1/D-07). user_id/group_id/resource_id 인덱스 추가. 각 테이블 COMMENT.

### TASK 2 — domain 계층
model:
- `Action`(enum VIEW/EDIT), `RoleName`(enum VIEWER/EDITOR/ADMIN + `implies(Action)` 역할 함의, 순수 도메인),
  `ResourceId`(record Long 래퍼 + null 가드), `MenuId`(record Long 래퍼 + null 가드).
- `GlobalRoleGrant`, `MenuGrant`, `ResourceGrant`, `Group`, `GroupMember`, `ResourceHierarchy`
  — @Entity 방식 A, @GeneratedValue(IDENTITY), protected 무인자 ctor, public setter 없음, 정적 팩토리.
  - MenuGrant/ResourceGrant: user_id/group_id nullable Long, `forUser()/forGroup()` 팩토리만(XOR 코드 강제).
  - created_at = `private Instant createdAt = Instant.now();` @Column(nullable=false, updatable=false) 단일 소스.
  - 컬럼명/타입/nullable 을 V3 와 일치(role=VARCHAR(50)↔@Enumerated(STRING)). Wave 3 ddl-auto:validate 검증 대상.

repository (write-port 6종, infrastructure import 없음):
- `GlobalRoleGrantRepository`(save/deleteByUserIdAndRole/findByUserId),
  `MenuGrantRepository`, `ResourceGrantRepository`(deleteByUserId.../deleteByGroupId...),
  `GroupRepository`(save/findById/findByName),
  `GroupMemberRepository`(save/deleteByGroupIdAndUserId/findByUserId/findByGroupId),
  `ResourceHierarchyRepository`(save/deleteByResourceId/findByResourceId).

### TASK 3 — application 계약
- `EffectiveGrantDto` — @Data @NoArgsConstructor @AllArgsConstructor flat class(record 아님), role/resourceId/source.
- `PermissionReadPort` — SampleQuery 형판, `findEffectiveGrants(@Param userId, @Param resourceId)`,
  `listAccessibleResourceIds(@Param userId, @Param action)`. SQL 은 Wave 2.
- `AuthorizationPort` — `evaluate(long, ResourceId, Action)` + `listObjects(long, Action)`. grant/revoke 미포함(D-04).
- `PermissionEvaluator` — @Service, application 배치(Open Q1/D-03). PermissionReadPort 생성자 주입.
  findEffectiveGrants → empty ⇒ false(default-deny) → RoleName.implies(action) anyMatch.
  Spring `org.springframework.security.access.PermissionEvaluator` import/구현 안 함(D-03).

## Verify 결과 (pass evidence)

- V3 게이트: `CREATE TABLE=6, REFERENCES=0, CHECK 존재, DEFAULT now() 없음` → **OK**
- `./gradlew compileJava` → **BUILD SUCCESSFUL**
- `./gradlew test --tests *ArchitectureTest*` → **BUILD SUCCESSFUL**
  - XML 확인: `tests="1" skipped="0" failures="0" errors="0"` → **ArchitectureTest GREEN**
  - PermissionEvaluator application 배치가 게이트 통과로 실증됨(Application mayOnlyAccessLayers Domain).
- PermissionEvaluator 내 `org.springframework.security` 출현 = Javadoc(D-03 설명) 1건뿐, **실제 import 0**.
- domain 의 `^import.*infrastructure` = **0** (6건은 모두 Javadoc 주석).

## Deviations

없음. 모든 KEY DECISIONS(D-03/D-04/D-07/D-09, XOR polymorphic, Open Q1=application) 준수.
RoleName 함의: VIEWER→{VIEW}, EDITOR→{VIEW,EDIT}, ADMIN→{VIEW,EDIT} (EDITOR⊇VIEWER, ADMIN⊇EDITOR).
role 컬럼 VARCHAR(50) ↔ @Enumerated(STRING) 매핑(Wave 3 validate 통과 예정).

## Wave 2/3 인계 메모

- Wave 2: infrastructure/jpa(6 *JpaRepository extends 포트), infrastructure/mybatis(AuthorizationMapper
  implements PermissionReadPort, 재귀 CTE + CYCLE/UNION 사이클 가드), PostgresAuthorizationAdapter
  (AuthorizationPort 구현). 선택: SpringSecurityPermissionEvaluatorAdapter(infrastructure/security).
- PermissionReadPort 구현이 없으므로 현재 PermissionEvaluator 빈은 런타임 주입 미해결 — Wave 2 @Mapper 가 채움.
  (compileJava/ArchitectureTest 는 영향 없음 — 컨텍스트 부팅은 Wave 3 통합 테스트에서.)
