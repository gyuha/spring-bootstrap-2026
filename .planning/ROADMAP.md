# 로드맵: Spring DDD 백엔드 베이스라인

## 개요

플랫폼 골격을 먼저 세우고, 그 위에 DDD 바운디드 컨텍스트를 쌓아올리는 순서로 진행한다. Identity 컨텍스트가 사용자 식별의 진실 공급원이고, BFF 인증은 최초 로그인 시 Identity의 신원 연결을 호출하며, Authorization은 Identity가 발급한 로컬 PK(userId)를 참조한다. 이 의존 방향이 빌드 순서를 결정한다.

## Milestones

- ✅ **v1.0 베이스라인 골격** — Phase 1~4 (shipped 2026-05-31) — 상세: [milestones/v1.0-ROADMAP.md](./milestones/v1.0-ROADMAP.md)
- 🔄 **v1.1 인증 웹 인터페이스** — Phase 5~6 (진행 중)

## Phases

<details>
<summary>✅ v1.0 베이스라인 골격 (Phase 1~4) — SHIPPED 2026-05-31</summary>

- [x] Phase 1: 플랫폼 골격 (5/5 plans) — Java 21 + Spring Boot MVC, 헥사고날 DDD, PostgreSQL/Flyway, JPA+MyBatis, Redis, 헬스체크
- [x] Phase 2: Identity 컨텍스트 (3/3 plans) — User 애그리거트(invite/linkIdentity/disable), VO, 도메인 이벤트
- [x] Phase 3: BFF 인증 (3/3 plans) — Spring Security OIDC, Redis 세션, 신원 연결 브리지, 불변 식별자 매칭
- [x] Phase 4: Authorization 컨텍스트 (3/3 plans) — 3계층 권한, 그룹, 재귀 CTE 계층 상속, PermissionEvaluator, AuthorizationPort 어댑터

전체 Phase 상세(Goal/SC/Plans)는 `milestones/v1.0-ROADMAP.md`에 아카이브됨.

</details>

### v1.1 인증 웹 인터페이스

- [ ] **Phase 5: BFF 인증 세션 엔드포인트** — 세션 상태 조회·로그아웃·로그인 진입 + SecurityConfig permitAll 조정 — 집계 의존 없는 순수 인증 흐름
- [ ] **Phase 6: 내 정보 신원·권한 집계** — `/api/auth/me`가 Identity(신원) + Authorization(권한) application을 교차 컨텍스트 집계 + ArchUnit 계층 규칙 정비

## Phase 상세

### Phase 5: BFF 인증 세션 엔드포인트

**Goal**: BFF SPA가 401 노이즈 없이 인증 여부를 판별하고, JSON 흐름으로 로그아웃하며, OIDC 로그인에 진입할 수 있는 순수 인증 엔드포인트가 auth/interfaces 계층에 존재한다 (Identity/Authorization 집계 의존 없음)
**Depends on**: Phase 4
**Requirements**: AUTH-06, AUTH-07, AUTH-10
**Success Criteria** (무엇이 TRUE여야 하는가):

  1. 비인증 상태에서 `GET /api/auth/session`을 호출하면 401 없이 `200 + {"authenticated": false}`가 반환되고, 인증 상태에서는 `200 + {"authenticated": true, "userId": ...}`가 반환된다
  2. 인증 상태에서 `POST /api/auth/logout`을 호출하면 302 redirect 없이 `204 No Content`가 반환되고, HttpSession 무효화·`SESSION` 쿠키 삭제·인증 클리어가 수행되어 이후 동일 세션 쿠키로의 보호 요청은 인증 실패한다
  3. `GET /api/auth/login`을 호출하면 OIDC authorization 진입점으로 리다이렉트되고 `returnTo` 쿼리 파라미터가 로그인 후 복귀 경로로 전달된다
  4. `/api/auth/session`과 `/api/auth/login`이 permitAll 매처로 비인증 접근 가능한 반면, 기존 보호 매처(`/api/**`)는 영향받지 않고 401 엔트리포인트를 유지한다

**Plans**: 3 plans

**Wave 1**

- [ ] 05-01-PLAN.md — SecurityConfig 외과적 수정(logoutUrl 재지정·204 핸들러·permitAll 추가·returnTo successHandler) + BffAuthIT logout 헬퍼 경로 갱신

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 05-02-PLAN.md — AuthController 신설(GET /api/auth/session, GET /api/auth/login + open-redirect 방지 + ObjectProvider 가드)

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 05-03-PLAN.md — BffAuthSessionIT 신규(SC#1~4 전체 단언 — Testcontainers + WireMock + MockMvc)

### Phase 6: 내 정보 신원·권한 집계

**Goal**: 인증 사용자가 `GET /api/auth/me` 한 번으로 자신의 신원(userId/email/status)과 권한 전체(전역 역할·메뉴·리소스)를 받을 수 있도록, auth/interfaces가 Identity·Authorization 두 컨텍스트의 application 서비스를 교차 호출해 집계한다
**Depends on**: Phase 5
**Requirements**: AUTH-08, AUTH-09, AUTH-11
**Success Criteria** (무엇이 TRUE여야 하는가):

  1. 인증 상태에서 `GET /api/auth/me`를 호출하면 userId·email·status(Identity application에서 조회한 로컬 User 신원)가 반환되고, 비인증 시 401이 반환된다
  2. 동일 `GET /api/auth/me` 응답에 전역 역할·메뉴 권한·리소스 권한(Authorization application에서 조회한 권한 전체)이 단일 응답으로 함께 포함된다
  3. ArchUnit 계층 의존 테스트가 GREEN 상태를 유지하며, `auth/interfaces`가 Identity·Authorization application 서비스를 호출하는 교차 컨텍스트 의존이 명시적으로 허용된 규칙으로 통과한다

**Plans**: 3 plans

**Wave 1**

- [ ] 06-01-PLAN.md — application read DTO 신규(UserView, PermissionView) + read 메서드 신설(IdentityApplicationService.findUser, AuthorizationApplicationService.findPermissions)

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 06-02-PLAN.md — MeResponse record 신규 + AuthController.me() 추가(신원·권한 교차 집계 + ArchUnit 사전 확인)

**Wave 3** *(blocked on Wave 2 completion)*

- [ ] 06-03-PLAN.md — BffAuthMeIT 신규(SC#1~3 전체 단언 — Testcontainers + WireMock + MockMvc + ArchUnit GREEN)

## 진행 현황

**실행 순서:**
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6

| Phase | Milestone | 완료된 플랜 | 상태 | 완료일 |
|-------|-----------|------------|------|--------|
| 1. 플랫폼 골격 | v1.0 | 5/5 | Complete | 2026-05-31 |
| 2. Identity 컨텍스트 | v1.0 | 3/3 | Complete | 2026-05-31 |
| 3. BFF 인증 | v1.0 | 3/3 | Complete | 2026-05-31 |
| 4. Authorization 컨텍스트 | v1.0 | 3/3 | Complete | 2026-05-31 |
| 5. BFF 인증 세션 엔드포인트 | v1.1 | 0/3 | Not started | - |
| 6. 내 정보 신원·권한 집계 | v1.1 | 0/3 | Not started | - |
