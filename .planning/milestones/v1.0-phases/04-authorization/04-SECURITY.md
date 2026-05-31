---
phase: 4
slug: authorization
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-31
---

# Phase 4 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> 등록부는 3개 PLAN의 `<threat_model>` 블록에서 구성됨 (register_authored_at_plan_time: true).
> 전 위협은 실 Postgres 통합 테스트(51 tests GREEN) + 정적 게이트(ArchUnit, `${}` grep)로 mitigation 실재가 검증되어 CLOSED.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| caller → PermissionEvaluator.evaluate | 신뢰되지 않은 userId/resource/action 입력이 판정 경계를 넘는다 | 식별자(Long)·VO·enum |
| application 읽기 포트 → Postgres(MyBatis 재귀 CTE) | userId/resourceId/action 이 SQL 로 바인딩되는 경계 | 식별자·action 문자열 |
| 서비스 인자 → JPA 애그리거트 쓰기 | grant/revoke 입력이 영속화 경계를 넘는다 | 부여 데이터(주체·대상·role) |
| resource_hierarchy → 재귀 CTE | 사이클 입력 시 무한 재귀 위험 | 자원 계층 그래프 |
| authorization → identity (user_id) | 크로스 컨텍스트 soft reference (물리 FK 없음) | userId(논리 참조) |
| identity ↔ authorization (linkIdentity) | INVITED→ACTIVE 전이 후 동일 userId 로 권한 표면화 | userId·신원 연결 |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-04-01 | Elevation of Privilege | PermissionEvaluator.evaluate (default-deny) | mitigate | grant 없으면 false 반환. PermissionEvaluatorTest(단위) + PermissionEvaluationIT 진리표 "없음" 케이스 GREEN | closed |
| T-04-02 | Elevation of Privilege | 도메인 vs Spring PermissionEvaluator 혼동 | mitigate | 도메인 서비스가 Spring `PermissionEvaluator`(hasPermission) 미구현(D-03). ArchitectureTest GREEN + application 의 `org.springframework.security` 실제 import 0건 | closed |
| T-04-03 | Tampering | 주체 XOR 위반(user_id+group_id 동시) | mitigate | 정적 팩토리 forUser/forGroup 만 노출(코드 강제) + DDL CHECK `(user_id IS NOT NULL) <> (group_id IS NOT NULL)`(DB 최종 방어) | closed |
| T-04-04 | Elevation of Privilege | 권한 상승(상속 오버리치) | mitigate | findEffectiveGrants 재귀 CTE 가 조상 방향(parent_resource_id)만 전개 — 역상속 금지. ListObjectsIT + PermissionEvaluationIT 진리표 상속 케이스 GREEN | closed |
| T-04-05 | Tampering (SQL injection) | AuthorizationMapper.xml | mitigate | 모든 파라미터 `#{}` 바인딩, `${}` 절대 금지. `grep -c '\${'` = 0 게이트 통과(재검증 완료) | closed |
| T-04-06 | Denial of Service | resource_hierarchy 재귀 CTE 사이클 | mitigate | ancestry CTE `CYCLE ... SET is_cycle USING path` + descendants `UNION` 중복 제거. ResourceHierarchyCycleIT 가 실 Postgres A↔B 사이클에 assertTimeoutPreemptively(5s)로 종료 증명 GREEN | closed |
| T-04-07 | Elevation of Privilege | revoke 미반영(stale grant) | mitigate | 판정 캐시 없음(D-06) — revoke 커밋 후 즉시 DB 미존재. PermissionEvaluationIT SC#1 즉시성 연속 단언 GREEN | closed |
| T-04-08 | Tampering | 주체 XOR 우회(쓰기 경로) | mitigate | AuthorizationApplicationService 가 forUser/forGroup 정적 팩토리만 호출 + DDL CHECK(DB 방어). T-04-03 과 동일 통제 | closed |
| T-04-09 | Spoofing / Integrity | SC#4 placeholder 우회(가짜 신원 연결로 검증 회피) | mitigate | InvitedGrantSurfacingIT 가 실제 IdentityApplicationService.linkIdentity 호출(mock/placeholder 금지 — VALIDATION 강제) GREEN | closed |
| T-04-SC | Tampering | npm/pip/cargo/gradle 의존성 설치 | accept | Phase 4 는 신규 패키지 설치 0건(기존 검증 의존성만 사용, D-11) — slopcheck 대상 없음 (AR-01) | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-04-SC | Phase 4 는 신규 의존성 설치 없음 — authorization 패키지는 기존 JPA/MyBatis/Postgres/Lombok 스택만 사용 | gyuha | 2026-05-31 |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-31 | 10 | 10 | 0 | gyuha (secure-phase, plan-time register 검증) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-31
