# Spring DDD 백엔드 베이스라인

## What This Is

`spring-backend-ddd-baseline.md`에 고정된 표준을 실제 코드로 구현한, 재사용 가능한 Spring 백엔드 골격이다. Java 21 + Spring Boot MVC, JPA(쓰기) + MyBatis(복잡 조회) 이원화, PostgreSQL, Redis, Flyway 위에 헥사고날 + DDD 패키지 구조를 갖추고, 거의 모든 업무 시스템에서 재사용되는 기반 컨텍스트(식별/인증/인가)를 제공한다. 이후 프로젝트는 이 골격을 복제해 자신의 업무 도메인만 채워 넣는다.

## Core Value

새 백엔드를 시작할 때마다 스택·아키텍처·인증/인가를 다시 결정하지 않도록, 검증된 DDD 골격과 식별/인증/인가 기반을 그대로 가져다 쓸 수 있게 한다.

## Current State

**v1.1 인증 웹 인터페이스 shipped (2026-05-31)** — PR #2(베이스라인 v1.0+v1.1). 다음 마일스톤 대기.

auth 컨텍스트에 BFF SPA용 인증 REST 표면 완성: `/api/auth/session`(401 노이즈 없는 상태 조회)·`/logout`(204)·`/login`(returnTo, open-redirect 방지)·`/me`(Identity 신원 + Authorization direct 권한 교차 집계). 전체 64 tests GREEN. ArchUnit 무변경으로 교차 컨텍스트 의존 허용.

**Next Milestone Goals:** 미정 — `/gsd-new-milestone`로 정의. 후보: 권한 effective 열거(그룹/계층 상속 포함, v1.1 D-02 한계 해소), 인가 변경 REST, 라이브 부팅 검증.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ Java 21 + Spring Boot MVC 기반 프로젝트 골격 (가상 스레드 활성화) — v1.0
- ✓ 헥사고날 + DDD 패키지 구조 (context × {domain, application, infrastructure, interfaces}) — v1.0 (ArchUnit 게이트로 강제)
- ✓ JPA(쓰기/기본 조회) + MyBatis(복잡 조회) 동일 DataSource 단일 트랜잭션 결합 — v1.0
- ✓ PostgreSQL + Flyway 스키마 버전 관리 (V1~V4) — v1.0
- ✓ Redis 세션 저장소 — v1.0
- ✓ Identity 컨텍스트 — User 애그리거트(invite/linkIdentity/disable), Email/ExternalId/UserStatus VO, 도메인 이벤트 — v1.0
- ✓ BFF 인증 — Spring Security OIDC, Redis 세션(토큰 브라우저 비노출), 최초 로그인 시 신원 연결, 불변 식별자 매칭(A-6) — v1.0
- ✓ Authorization 컨텍스트 — 3계층 권한(전역 역할/메뉴/리소스), 그룹, 재귀 CTE 계층 상속, PermissionEvaluator 도메인 서비스 — v1.0
- ✓ 인가 포트/어댑터(A-5) — 앱 내부 구현(Postgres + 재귀 CTE), ListObjects는 MyBatis 읽기 모델, 교체 가능 — v1.0
- ✓ BFF 인증 세션 엔드포인트 — `/api/auth/session`(비인증 200, 401 노이즈 제거)·`/logout`(204)·`/login`(OIDC 진입 + returnTo, open-redirect 방지) — v1.1 (AUTH-06/07/10)
- ✓ 내 정보 집계 — `/api/auth/me`가 Identity(신원) + Authorization(direct 권한) 교차 집계, CQRS-lite read DTO, ArchUnit 무변경 GREEN — v1.1 (AUTH-08/09/11)

### Active

<!-- Current scope. Building toward these. -->

(다음 마일스톤에서 정의 — `/gsd-new-milestone`)

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- 업무 도메인(Project/Assignment 등 5.3 예시) — 베이스라인은 도메인 비종속. 후속 프로젝트가 채움
- 프론트엔드/UI — 문서 범위가 백엔드 한정
- 외부 인가 엔진(OpenFGA 등) 어댑터 구현 — 포트는 열어두되 내부 어댑터부터 시작(A-7 트립와이어 전까지 보류)
- 외부 IdP 디렉터리 전체 동기화 — 위임 권한 범위로 한정(NFR-03)
- Spring AI 연동 — 향후 항목, v1.0 비포함

## Context

- 베이스라인 명세 원문: `spring-backend-ddd-baseline.md` (문서 버전 v1.0). 스택·아키텍처 결정(A-1~A-7)·DDD 빌딩 블록·기능별 적용 패턴·NFR이 모두 여기에 고정되어 있다.
- 직전 프로젝트(인증 중심: register/login/refresh/logout/social, /me)는 삭제됨. 이 골격은 그 학습을 반영해 DDD 구조 위에서 다시 세운다.
- 배포 환경 비종속(온프레미스/클라우드 무관). 외부 의존 최소화.
- **v1.0 shipped 상태:** 4 컨텍스트(platform/identity/auth/authorization) 구현 완료. 전체 51 tests GREEN(실 Postgres Testcontainers). 코드 PR #2(Phase 1+2+3+4). 검증 인프라 = Testcontainers(postgres:16/redis:7) + WireMock(OIDC). 로컬 라이브 실행은 8080/5432/6379가 타 프로젝트와 충돌 시 차단될 수 있음.
- **인가 HTTP API 미포함(의도):** authorization은 도메인/판정/영속 + 프로그래밍 API(AuthorizationApplicationService/PermissionEvaluator/AuthorizationPort)만 제공. REST 노출은 복제 업무 프로젝트가 채울 영역.

## Constraints

- **Tech stack**: Java 21 / Spring Boot + MVC / Spring Data JPA + MyBatis / PostgreSQL / Redis / Flyway / Spring Security OAuth2-OIDC / Lombok — 베이스라인 2장에 고정. 임의 대체 금지
- **Architecture**: MVC + 가상 스레드(WebFlux 아님), JPA=쓰기·MyBatis=복잡 조회, BFF 인증(토큰 브라우저 비노출), 인증/인가 분리, 인가=포트/어댑터, 불변 식별자 매칭 — A-1~A-6
- **DDD**: 모든 기능은 도메인 모델에서 출발(테이블 우선 금지). 바운디드 컨텍스트 → 애그리거트/VO/불변식 → 영속성 순. JPA 방식 A(실용형) 기본, 컨텍스트별 B 선택 가능
- **Persistence integrity**: 하나의 트랜잭션 = 하나의 애그리거트 수정. 컨텍스트 간 변경은 도메인 이벤트 + 최종적 일관성
- **Security**: BFF 패턴, 액세스 토큰은 Redis 서버 세션에만 보관 (NFR-02)

## Key Decisions

<!-- Decisions that constrain future work. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| greenfield `gsd-new-project`로 초기화 (`sg-new`/new-milestone 아님) | PROJECT/ROADMAP/STATE 부재 — milestone 워크플로우는 기존 프로젝트 전제라 깨짐 | ✓ Good — 4 phase 무사 완주 |
| scope = 골격 + Identity/Authorization/BFF 인증 | 문서가 A-3·A-4·A-6 인증을 "거의 항상 재사용"이라 명시, 직전 프로젝트도 인증 중심 | ✓ Good — v1.0 전부 shipped |
| 인가 구현 = 앱 내부(Postgres 재귀 CTE) 먼저, 포트로 외부 엔진 교체 가능 | A-5/A-7 — 서버 추가 없이 시작, 복잡도 한계 시 외부 엔진 재검토 | ✓ Good — AuthorizationPort 교체 가능 구조 실증(SC#5) |
| JPA 방식 A(엔티티=애그리거트) 기본 채택 | 사내 업무 시스템엔 충분, 매핑 보일러플레이트 절감 (4.4) | ✓ Good — 4 컨텍스트 전부 방식 A로 구현 |
| 적용된 Flyway 마이그레이션 불변 — 추가는 새 V 번호로 | V3 편집이 영속 dev DB checksum mismatch로 부팅 실패(Testcontainers가 가림) | ⚠️ Revisit — ArchUnit/게이트로 자동 차단 미흡, 다음 마일스톤 강화 후보 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-31 — v1.1 인증 웹 인터페이스 shipped (PR #2)*
