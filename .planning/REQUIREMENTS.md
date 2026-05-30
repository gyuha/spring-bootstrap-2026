# Requirements: Spring DDD 백엔드 베이스라인

**Defined:** 2026-05-30
**Core Value:** 새 백엔드를 시작할 때마다 스택·아키텍처·인증/인가를 다시 결정하지 않도록, 검증된 DDD 골격과 식별/인증/인가 기반을 그대로 가져다 쓸 수 있게 한다.

## v1 Requirements

마일스톤 v1.0의 범위. 각 항목은 로드맵 phase에 매핑된다.

### Platform (플랫폼 골격)

- [ ] **PLAT-01**: Java 21 + Spring Boot MVC 애플리케이션이 가상 스레드 활성화 상태로 기동한다
- [ ] **PLAT-02**: 헥사고날 + DDD 패키지 구조(`<context>/{domain,application,infrastructure,interfaces}`)가 컨텍스트별로 잡혀 있고 의존 방향이 안쪽으로만 향한다
- [ ] **PLAT-03**: PostgreSQL 연결과 Flyway 마이그레이션으로 스키마가 버전 관리된다
- [ ] **PLAT-04**: JPA(쓰기/기본 조회)와 MyBatis(복잡 조회)가 동일 DataSource·단일 트랜잭션으로 동작한다
- [ ] **PLAT-05**: Redis 연결이 세션 저장소로 구성된다
- [ ] **PLAT-06**: 헬스체크 엔드포인트로 앱·DB·Redis 상태를 확인할 수 있다

### Identity (식별/온보딩 컨텍스트)

- [ ] **IDEN-01**: 관리자가 미로그인 사용자를 이메일로 초대(invite)하면 INVITED 상태로 등록된다
- [ ] **IDEN-02**: 사용자가 최초 로그인하면 외부 식별자(IdP oid)가 신원에 연결(linkIdentity)되고 INVITED→ACTIVE로 전이한다
- [ ] **IDEN-03**: 동일 신원에 외부 식별자를 두 번 연결하려는 시도는 거부된다
- [ ] **IDEN-04**: 관리자가 사용자를 비활성화(disable)하면 DISABLED로 전이하고 `UserDisabled` 이벤트가 발행된다
- [ ] **IDEN-05**: 이메일은 유일하고, 로그인 후 매칭은 불변 식별자(로컬 PK)로만 이루어진다
- [ ] **IDEN-06**: IdP 출처 필드(이름 등)는 로그인 시 갱신되고, 관리자 입력 필드는 로그인으로 덮어쓰이지 않는다

### Auth (BFF 인증/세션)

- [ ] **AUTH-01**: 사용자가 OIDC IdP를 통해 로그인할 수 있다 (IdP 비종속)
- [ ] **AUTH-02**: 액세스/리프레시 토큰은 브라우저에 노출되지 않고 Redis 서버 세션에만 보관된다
- [ ] **AUTH-03**: 프론트는 쿠키 기반 세션으로 백엔드와 통신한다
- [ ] **AUTH-04**: 로그아웃 시 서버 세션이 무효화된다
- [ ] **AUTH-05**: 최초 로그인 시 Auth가 Identity 컨텍스트의 신원 연결 연산을 호출한다 (5.1 횡단 인프라 경계 준수)

### Authorization (인가 컨텍스트)

- [ ] **AUTHZ-01**: 사용자에게 전역 역할을 부여/회수할 수 있다
- [ ] **AUTHZ-02**: 사용자/그룹에게 메뉴 권한을 부여/회수할 수 있다 (MenuGrant)
- [ ] **AUTHZ-03**: 사용자/그룹에게 리소스 권한을 부여/회수할 수 있다 (ResourceGrant)
- [ ] **AUTHZ-04**: 그룹을 생성하고 사용자를 그룹에 가입/탈퇴시킬 수 있다 (Group/GroupMember)
- [ ] **AUTHZ-05**: 리소스 계층을 정의하면 상위 리소스 권한이 하위로 상속된다 (ResourceHierarchy)
- [ ] **AUTHZ-06**: `PermissionEvaluator.evaluate(userId, resource, action)`가 직접 부여 ∪ 그룹 ∪ 상위 상속을 합산해 판정한다
- [ ] **AUTHZ-07**: "현재 사용자가 접근 가능한 리소스 목록(ListObjects)"을 MyBatis 읽기 모델(재귀 CTE)로 조회한다
- [ ] **AUTHZ-08**: 인가는 `AuthorizationPort` 인터페이스 뒤에 있고, 앱 내부(Postgres) 어댑터로 구현되어 외부 엔진으로 교체 가능하다
- [ ] **AUTHZ-09**: 미로그인(INVITED) 사용자에게 부여한 권한이 저장되고, 로그인 시 자동으로 효력을 가진다

## Future Requirements

향후 릴리스로 연기. 추적하되 현재 로드맵에는 미포함.

### External Authorization Engine

- **AUTHZ-X1**: OpenFGA 등 외부 인가 엔진 어댑터 + 부패 방지 계층(ACL)
- **AUTHZ-X2**: dual-write 정합성 정책(이벤트 기반 보정, 재시도)

### Platform Extensions

- **PLAT-X1**: Spring AI 연동
- **PLAT-X2**: 외부 IdP 디렉터리 위임 권한 범위 연동

## Out of Scope

명시적 제외. 스코프 크리프 방지용.

| Feature | Reason |
|---------|--------|
| 업무 도메인(Project/Assignment 등 5.3 예시) | 베이스라인은 도메인 비종속 — 후속 프로젝트가 자신의 바운디드 컨텍스트로 채움 |
| 프론트엔드/UI | 문서 범위가 백엔드 한정 |
| 외부 IdP 그룹 동기화 | 그룹은 앱 자체 관리가 기본(통제·단순성, 5.5) |
| 외부 디렉터리 전체 동기화 | 위임 권한 범위로 한정(NFR-03) |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PLAT-01 | Phase 1 | Pending |
| PLAT-02 | Phase 1 | Pending |
| PLAT-03 | Phase 1 | Pending |
| PLAT-04 | Phase 1 | Pending |
| PLAT-05 | Phase 1 | Pending |
| PLAT-06 | Phase 1 | Pending |
| IDEN-01 | Phase 2 | Pending |
| IDEN-02 | Phase 2 | Pending |
| IDEN-03 | Phase 2 | Pending |
| IDEN-04 | Phase 2 | Pending |
| IDEN-05 | Phase 2 | Pending |
| IDEN-06 | Phase 2 | Pending |
| AUTH-01 | Phase 3 | Pending |
| AUTH-02 | Phase 3 | Pending |
| AUTH-03 | Phase 3 | Pending |
| AUTH-04 | Phase 3 | Pending |
| AUTH-05 | Phase 3 | Pending |
| AUTHZ-01 | Phase 4 | Pending |
| AUTHZ-02 | Phase 4 | Pending |
| AUTHZ-03 | Phase 4 | Pending |
| AUTHZ-04 | Phase 4 | Pending |
| AUTHZ-05 | Phase 4 | Pending |
| AUTHZ-06 | Phase 4 | Pending |
| AUTHZ-07 | Phase 4 | Pending |
| AUTHZ-08 | Phase 4 | Pending |
| AUTHZ-09 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 26 total
- Mapped to phases: 26
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-30*
*Last updated: 2026-05-30 after roadmap creation*
