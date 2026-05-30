# 로드맵: Spring DDD 백엔드 베이스라인

## 개요

플랫폼 골격을 먼저 세우고, 그 위에 DDD 바운디드 컨텍스트를 쌓아올리는 순서로 진행한다. Identity 컨텍스트가 사용자 식별의 진실 공급원이고, BFF 인증은 최초 로그인 시 Identity의 신원 연결을 호출하며, Authorization은 Identity가 발급한 로컬 PK(userId)를 참조한다. 이 의존 방향이 빌드 순서를 결정한다.

## 단계 (Phases)

**Phase 번호 규칙:**
- 정수 단계 (1, 2, 3): 계획된 마일스톤 작업
- 소수 단계 (2.1, 2.2): 긴급 삽입 작업 (INSERTED 표시)

소수 단계는 인접한 정수 사이에서 숫자 순서대로 실행된다.

- [ ] **Phase 1: 플랫폼 골격** - Java 21 + Spring Boot MVC, 헥사고날 DDD 패키지, PostgreSQL/Flyway, JPA+MyBatis, Redis, 헬스체크
- [ ] **Phase 2: Identity 컨텍스트** - User 애그리거트(invite/linkIdentity/disable), Email/ExternalId/UserStatus VO, 도메인 이벤트
- [ ] **Phase 3: BFF 인증** - Spring Security OIDC, Redis 세션, 최초 로그인 신원 연결, 불변 식별자 매칭
- [ ] **Phase 4: Authorization 컨텍스트** - 3계층 권한, 그룹, 계층 상속, PermissionEvaluator, AuthorizationPort 어댑터

## Phase 상세

### Phase 1: 플랫폼 골격
**Goal**: 모든 후속 컨텍스트가 올라설 수 있는 기술 기반과 프로젝트 골격이 동작한다
**Depends on**: 없음 (첫 번째 단계)
**Requirements**: PLAT-01, PLAT-02, PLAT-03, PLAT-04, PLAT-05, PLAT-06
**Success Criteria** (무엇이 TRUE여야 하는가):
  1. 애플리케이션이 기동하고 가상 스레드 활성화 로그가 확인된다
  2. `<context>/{domain,application,infrastructure,interfaces}` 패키지 구조가 존재하며 의존 방향 위반이 없다
  3. Flyway 마이그레이션이 실행되고 `flyway_schema_history`에 기록이 남는다
  4. JPA 쓰기와 MyBatis 조회가 동일 DataSource·단일 트랜잭션에서 동작한다
  5. `/actuator/health`가 앱·DB(PostgreSQL)·Redis 상태를 모두 반환한다
**Plans**: 5 plans

Plans:
- [ ] 01-01-PLAN.md — Gradle 빌드 설정 + BaselineApplication (Java 21, 의존성, flyway-database-postgresql)
- [ ] 01-02-PLAN.md — Wave 0 테스트 스텁 (ArchitectureTest, AbstractIntegrationTest, 통합 테스트 파일 6종)
- [ ] 01-03-PLAN.md — 앱 설정 + Flyway 마이그레이션 + Docker Compose (application.yml, V1 SQL, compose.yaml)
- [ ] 01-04-PLAN.md — platform/common 계층 골격 (SampleEntity, SampleMapper, application/interfaces 계층)
- [ ] 01-05-PLAN.md — 통합 테스트 완성 및 전체 스위트 GREEN (SC#1~5 모두 검증)

### Phase 2: Identity 컨텍스트
**Goal**: 사용자 식별의 진실 공급원인 Identity 도메인이 초대·신원 연결·비활성화 생명주기를 DDD 애그리거트로 구현한다
**Depends on**: Phase 1
**Requirements**: IDEN-01, IDEN-02, IDEN-03, IDEN-04, IDEN-05, IDEN-06
**Success Criteria** (무엇이 TRUE여야 하는가):
  1. `User.invite(email)` 호출 시 INVITED 상태로 저장되고 이메일 유일성 제약이 적용된다
  2. `User.linkIdentity(externalId)` 호출 시 INVITED→ACTIVE 전이가 일어나고 같은 신원에 두 번 연결 시 예외가 발생한다
  3. `User.disable()` 호출 시 DISABLED로 전이하고 `UserDisabled` 도메인 이벤트가 발행된다
  4. IdP 출처 필드(이름 등)는 linkIdentity 시 갱신되고, 관리자 입력 필드는 덮어쓰이지 않는다
  5. 로그인 후 매칭은 이메일이 아닌 불변 로컬 PK(`User.id`)로만 이루어진다
**Plans**: TBD

### Phase 3: BFF 인증
**Goal**: 브라우저에 토큰을 노출하지 않는 BFF 인증이 동작하고, 최초 로그인 시 Identity 신원 연결이 호출된다
**Depends on**: Phase 2
**Requirements**: AUTH-01, AUTH-02, AUTH-03, AUTH-04, AUTH-05
**Success Criteria** (무엇이 TRUE여야 하는가):
  1. OIDC IdP를 통해 로그인 후 액세스/리프레시 토큰이 Redis 서버 세션에만 저장되고 응답 쿠키에는 세션 ID만 있다
  2. 프론트엔드(또는 HTTP 클라이언트)는 쿠키 기반 세션만으로 인증된 API를 호출할 수 있다
  3. 최초 로그인 시 Identity 컨텍스트의 `linkIdentity` 연산이 호출되어 INVITED→ACTIVE 전이가 발생한다
  4. 로그아웃 요청 시 Redis 서버 세션이 무효화되고 이후 동일 세션 쿠키로의 요청은 인증 실패한다
**Plans**: TBD

### Phase 4: Authorization 컨텍스트
**Goal**: 전역 역할/메뉴/리소스 3계층 권한과 그룹·계층 상속이 포트/어댑터 구조로 구현되어 PermissionEvaluator가 통합 판정을 내린다
**Depends on**: Phase 3
**Requirements**: AUTHZ-01, AUTHZ-02, AUTHZ-03, AUTHZ-04, AUTHZ-05, AUTHZ-06, AUTHZ-07, AUTHZ-08, AUTHZ-09
**Success Criteria** (무엇이 TRUE여야 하는가):
  1. userId에 전역 역할을 부여/회수하면 `PermissionEvaluator.evaluate()` 결과에 즉시 반영된다
  2. `PermissionEvaluator.evaluate(userId, resource, action)`가 직접 부여 ∪ 그룹 ∪ 상위 리소스 상속을 합산해 판정한다
  3. 리소스 계층을 정의하면 상위 리소스 권한이 하위로 상속되어 ListObjects 조회 결과에 포함된다
  4. INVITED 상태 사용자에게 부여한 권한이 DB에 저장되고, ACTIVE 전이 후 해당 권한이 `evaluate()` 결과에 반영된다
  5. `AuthorizationPort` 인터페이스가 존재하고 Postgres 내부 어댑터로 구현되어 교체 가능한 구조다
**Plans**: TBD

## 진행 현황

**실행 순서:**
Phase 1 → Phase 2 → Phase 3 → Phase 4

| Phase | 완료된 플랜 | 상태 | 완료일 |
|-------|------------|------|--------|
| 1. 플랫폼 골격 | 0/5 | Planning complete | - |
| 2. Identity 컨텍스트 | 0/TBD | Not started | - |
| 3. BFF 인증 | 0/TBD | Not started | - |
| 4. Authorization 컨텍스트 | 0/TBD | Not started | - |
