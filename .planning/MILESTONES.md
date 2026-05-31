# Milestones

## v1.1 인증 웹 인터페이스 (Shipped: 2026-05-31)

**Phases completed:** 2 phases, 6 plans (PR #2 — 베이스라인 v1.0+v1.1)

**Delivered:** BFF SPA가 소비할 인증 웹 인터페이스(REST)를 auth 컨텍스트에 추가.

**Key accomplishments:**

- **Phase 5 BFF 인증 세션 엔드포인트** — `GET /api/auth/session`(비인증 200 `{authenticated:false}`, 401 노이즈 제거), `POST /api/auth/logout`(302→204, 세션·Redis 무효화), `GET /api/auth/login`(OIDC 진입 래퍼 + returnTo, open-redirect 방지: 절대/프로토콜-상대/역슬래시/인코딩 거부), SecurityConfig(permitAll·RETURN_TO successHandler·registration id @Value 외부화)
- **Phase 6 내 정보 신원·권한 집계** — `GET /api/auth/me`가 Identity(userId/email/status) + Authorization(역할/메뉴/리소스 direct 권한) application을 교차 호출해 단일 응답 집계. CQRS-lite read DTO(UserView/PermissionView/MeResponse, 도메인·영속 애너테이션 interfaces 비노출). ArchUnit 무변경 GREEN(교차 컨텍스트 의존 허용, D-06)
- **검증** — 전체 64 tests GREEN(v1.0 51 + 신규 13, 실 Postgres/Redis Testcontainers + WireMock OIDC + MockMvc). 각 Phase plan-checker 12/12 PASS + 코드 리뷰(open-redirect 우회 C1, unguarded principal I-1) 반영

**Known deferred items at close:**

- 권한 집계 = direct only(D-02) — 그룹 멤버십·리소스 계층(재귀 CTE) 상속 미포함. effective 열거는 신규 CTE 필요로 범위 초과. 3버킷 구조 유지해 향후 확장 가능. SPA가 상속 권한 표시 필요 시 다음 마일스톤 후보.
- 라이브 부팅(`task run`) 재확인 미수행(로컬 포트 혼잡). Testcontainers가 실 인프라 검증 대체.
- PR #2 미머지(main 대기) — v1.0+v1.1 통합 리뷰/머지 후속.

---

## v1.0 베이스라인 골격 (Shipped: 2026-05-31)

**Phases completed:** 4 phases, 14 plans

**Delivered:** `spring-backend-ddd-baseline.md` 표준을 실제 코드로 구현한, 재사용 가능한 Spring 헥사고날+DDD 백엔드 골격 — 식별/인증/인가 기반 컨텍스트 포함.

**Key accomplishments:**

- **Phase 1 플랫폼 골격** — Java 21 + Spring Boot MVC(가상 스레드, WebFlux 아님), DDD 4계층 패키지 구조, JPA(쓰기)+MyBatis(복잡 조회) 단일 트랜잭션, Flyway, Redis 세션, ArchUnit 계층 의존 게이트, `/actuator/health`
- **Phase 2 Identity 컨텍스트** — User 애그리거트 생명주기(초대 INVITED → 신원 연결 ACTIVE → 비활성화 DISABLED), 불변 로컬 PK 매칭, `UserDisabled` 도메인 이벤트
- **Phase 3 BFF 인증** — OIDC IdP 로그인(비종속), 액세스/리프레시 토큰 Redis 서버 세션 전용(브라우저 비노출, NFR-02), 쿠키 세션 + CSRF, 최초 로그인 시 Identity.linkIdentity 호출 브리지
- **Phase 4 Authorization 컨텍스트** — 3계층 권한(전역/메뉴/리소스)·그룹·재귀 CTE 계층 상속, PermissionEvaluator(default-deny·역할 함의 VIEWER⊂EDITOR⊂ADMIN), 교체 가능 AuthorizationPort(Postgres 어댑터), 사이클 가드, INVITED soft reference
- **검증** — 전체 51 tests GREEN(실 Postgres Testcontainers, H2 금지), 4 phase 전부 code review + secure-phase(threats_open:0) 통과, MyBatis `#{}` 전용(`${}` 0건)

**Known deferred items at close: 2**

- `260530-vep-swagger` quick task — 실제 shipped(커밋 54cb27a)이나 audit 상태 감지 누락(missing)
- Phase 4 라이브 부팅 재확인 — Flyway V3 복원+V4 분리 수정(6802331) 후 영속 dev DB `task run` 재부팅 확인 미수행(Testcontainers는 GREEN). `04-UAT.md` Acknowledged Gaps 참조

---
