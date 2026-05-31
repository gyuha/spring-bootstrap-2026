# Milestones

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
