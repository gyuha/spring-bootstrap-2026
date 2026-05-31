# 로드맵: Spring DDD 백엔드 베이스라인

## 개요

플랫폼 골격을 먼저 세우고, 그 위에 DDD 바운디드 컨텍스트를 쌓아올리는 순서로 진행한다. Identity 컨텍스트가 사용자 식별의 진실 공급원이고, BFF 인증은 최초 로그인 시 Identity의 신원 연결을 호출하며, Authorization은 Identity가 발급한 로컬 PK(userId)를 참조한다. 이 의존 방향이 빌드 순서를 결정한다.

## Milestones

- ✅ **v1.0 베이스라인 골격** — Phase 1~4 (shipped 2026-05-31) — 상세: [milestones/v1.0-ROADMAP.md](./milestones/v1.0-ROADMAP.md)

## Phases

<details>
<summary>✅ v1.0 베이스라인 골격 (Phase 1~4) — SHIPPED 2026-05-31</summary>

- [x] Phase 1: 플랫폼 골격 (5/5 plans) — Java 21 + Spring Boot MVC, 헥사고날 DDD, PostgreSQL/Flyway, JPA+MyBatis, Redis, 헬스체크
- [x] Phase 2: Identity 컨텍스트 (3/3 plans) — User 애그리거트(invite/linkIdentity/disable), VO, 도메인 이벤트
- [x] Phase 3: BFF 인증 (3/3 plans) — Spring Security OIDC, Redis 세션, 신원 연결 브리지, 불변 식별자 매칭
- [x] Phase 4: Authorization 컨텍스트 (3/3 plans) — 3계층 권한, 그룹, 재귀 CTE 계층 상속, PermissionEvaluator, AuthorizationPort 어댑터

전체 Phase 상세(Goal/SC/Plans)는 `milestones/v1.0-ROADMAP.md`에 아카이브됨.

</details>

## 진행 현황

| Phase | Milestone | 완료된 플랜 | 상태 | 완료일 |
|-------|-----------|------------|------|--------|
| 1. 플랫폼 골격 | v1.0 | 5/5 | Complete | 2026-05-31 |
| 2. Identity 컨텍스트 | v1.0 | 3/3 | Complete | 2026-05-31 |
| 3. BFF 인증 | v1.0 | 3/3 | Complete | 2026-05-31 |
| 4. Authorization 컨텍스트 | v1.0 | 3/3 | Complete | 2026-05-31 |
