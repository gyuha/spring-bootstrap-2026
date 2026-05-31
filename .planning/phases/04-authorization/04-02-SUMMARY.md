# Phase 04 Wave 2 — Authorization 인프라 어댑터 + 재귀 CTE + 서비스 (SUMMARY)

**Completed:** 2026-05-31
**Scope:** Wave 2 of 3 — infrastructure(JPA 어댑터 6 + MyBatis 재귀 CTE + Postgres 어댑터) + application 서비스.
Wave 3(Testcontainers 통합 테스트)는 미구현(의도적). 본 웨이브는 compile + 구조 + ArchUnit 까지만 검증.

## 생성 파일 (created)

### TASK 1 — JPA 쓰기 어댑터 6 (authorization/infrastructure/jpa/)
모두 `interface XxxJpaRepository extends JpaRepository<Xxx, Long>, XxxRepository {}` 형(UserJpaRepository 형판).
Wave-1 쓰기 포트 메서드는 전부 Spring Data 파생 쿼리로 자동 구현 — @Query 불필요(시그니처 0건 추가).
- `GlobalRoleGrantJpaRepository` (deleteByUserIdAndRole / findByUserId)
- `MenuGrantJpaRepository` (deleteByUserIdAndMenuId / deleteByGroupIdAndMenuId / findByUserId / findByGroupId)
- `ResourceGrantJpaRepository` (deleteByUserIdAndResourceId / deleteByGroupIdAndResourceId / findByUserId / findByGroupId)
- `GroupJpaRepository` (findByName)
- `GroupMemberJpaRepository` (deleteByGroupIdAndUserId / findByUserId / findByGroupId)
- `ResourceHierarchyJpaRepository` (deleteByResourceId / findByResourceId)

### TASK 2 — MyBatis 재귀 CTE + Postgres 어댑터
- `authorization/infrastructure/mybatis/AuthorizationMapper.java` — `@Mapper interface AuthorizationMapper extends PermissionReadPort {}` (SampleMapper 형판, @MapperScan 불필요).
- `src/main/resources/mybatis/AuthorizationMapper.xml` — namespace = 어댑터 FQCN.
  - `findEffectiveGrants(userId, resourceId)`: 단일 `WITH RECURSIVE` — (1) `ancestry`: 대상 resourceId 자기 + `parent_resource_id` ONLY 따라 올라가는 **조상 방향 전개**(T-04-04 역상속 차단), `CYCLE resource_id SET is_cycle USING path` 사이클 가드(Pitfall 1); (2) `my_groups`: userId 소속 group_id. → `resource_grants WHERE resource_id IN ancestry AND (user_id = #{userId} OR group_id IN my_groups)` raw 행 반환(role/resourceId/source). 자기 리소스 직접/그룹 부여는 ancestry 베이스(`SELECT #{resourceId}`)로 포함. source = direct/group/inherited. 역할 함의는 도메인(PermissionEvaluator)에서 적용.
  - `listAccessibleResourceIds(userId, action)`: 부여 루트(직접 + 그룹 resource_grants, action 역할함의 SQL 필터 — VIEW=전역할, EDIT=EDITOR/ADMIN) → `descendants` 하향(parent→child) 재귀, `UNION`(중복 제거) 사이클 가드 → `DISTINCT resource_id`.
  - **전 파라미터 `#{}` 바인딩만 — `${}` 0건**(T-04-05).
- `authorization/infrastructure/PostgresAuthorizationAdapter.java` — `@Component implements AuthorizationPort`. evaluate → PermissionEvaluator.evaluate 위임(도메인 판정 재사용), listObjects → AuthorizationMapper.listAccessibleResourceIds 위임(`action.name()` 전달).

### TASK 3 — application 서비스 (authorization/application/)
- `AuthorizationApplicationService.java` — `@Service @RequiredArgsConstructor @Transactional`. **6개 도메인 쓰기 포트만 주입**(infrastructure 무주입 — application→domain 게이트 준수). 조율만(불변식은 애그리거트):
  - grantGlobalRole/revokeGlobalRole (AUTHZ-01)
  - grantMenuToUser/grantMenuToGroup/revokeMenuFromUser/revokeMenuFromGroup (AUTHZ-02)
  - grantResourceToUser/grantResourceToGroup/revokeResourceFromUser/revokeResourceFromGroup (AUTHZ-03)
  - createGroup(name)→groupId / addMember / removeMember (AUTHZ-04)
  - defineHierarchy(resourceId, parentResourceId) (AUTHZ-05)
  - 각 grant 은 도메인 `of/forUser/forGroup` 팩토리 → save. revoke 는 포트 deleteBy.... userId 존재 검사 없음(soft ref D-07 / AUTHZ-09 추가 코드 불필요).

## 수정 파일 (modified)
- `src/main/resources/application.yml` — `mybatis.mapper-locations: classpath*:mybatis/*.xml` 추가(XML namespace 바인딩). 기존 `map-underscore-to-camel-case: true` 유지.

## Verify 결과 (pass evidence)
- `./gradlew compileJava` → **BUILD SUCCESSFUL** (TASK 1·2·3 전부 컴파일).
- `grep -c '\${' AuthorizationMapper.xml` = **0** → `no-dollar-brace OK` (T-04-05 통과).
- `./gradlew test --tests *ArchitectureTest*` → **BUILD SUCCESSFUL**.
  - XML: `tests="1" skipped="0" failures="0" errors="0"` → **ArchitectureTest GREEN**.
  - application→infrastructure 위반 **0**(서비스는 도메인 포트만 주입). infrastructure→domain/application 허용 경로만 사용.

## Deviations
- XML 주석에 처음 `${}` 리터럴이 들어가 no-dollar-brace 게이트가 1건으로 실패 → 주석 문구를 "문자열 치환 구문"으로 바꿔 0건 달성. SQL 본문엔 애초에 `${}` 없음(기능 영향 0).
- 그 외 계획 대비 편차 없음. 선택 항목인 `SpringSecurityPermissionEvaluatorAdapter`(infrastructure/security)는 베이스라인 필수 아님(RESEARCH §428, D-03)이라 미생성.

## Wave 3 인계 메모
- 런타임 CTE 정확성(조상 전개·사이클 가드·하향 상속·합산 판정 진리표)은 Wave 3 Testcontainers(postgres:16)에서 검증. 재귀 CTE 는 실 Postgres 에서만 정확(D-10, H2 금지).
- `PostgresAuthorizationAdapter` 가 `PermissionEvaluator`(기존 application 빈)와 `AuthorizationMapper`(@Mapper)를 묶어 `AuthorizationPort` 빈을 완성 — Wave 1 미해결이던 PermissionReadPort 런타임 주입은 @Mapper 가 채움. 컨텍스트 부팅 검증은 Wave 3 통합 테스트.
- `mybatis.mapper-locations` 추가로 XML 매퍼 스캔 활성화 — 기존 SampleMapper(@Select 인라인)는 XML 없어 영향 없음.
