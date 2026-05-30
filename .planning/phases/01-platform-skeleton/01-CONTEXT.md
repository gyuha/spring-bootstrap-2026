# Phase 1: 플랫폼 골격 - Context

**Gathered:** 2026-05-30
**Status:** Ready for planning

<domain>
## Phase Boundary

후속 바운디드 컨텍스트(Identity / Authorization / BFF 인증)가 모두 올라설 **기술 기반 + 프로젝트 골격**을 동작 상태로 세운다. 범위: 앱 기동(가상 스레드), 헥사고날+DDD 패키지 구조, PostgreSQL+Flyway, JPA+MyBatis 동일 DataSource 단일 트랜잭션 결합, Redis 세션 저장소, 헬스체크.

**범위 밖 (다른 Phase 소관):** Identity/Authorization/인증의 실제 도메인 모델·유스케이스. Phase 1은 골격과 인프라만 — 업무 도메인은 비종속.

</domain>

<decisions>
## Implementation Decisions

### 빌드 & 모듈 구조
- **D-01:** **Gradle (Kotlin DSL) 단일 모듈**. 컨텍스트 분리는 패키지로 표현하고, 멀티모듈은 채택하지 않는다 — 복제되는 골격에 조기 복잡도를 주지 않기 위함. 의존 방향은 빌드 분리가 아니라 ArchUnit으로 강제(D-05 참조).

### 패키지 & 컨텍스트 구성
- **D-02:** 루트 패키지 = **`com.anchors.baseline`**. 기준 문서 §4.2의 `com.company.app` 플레이스홀더를 이 값으로 대체한다.
- **D-03:** Phase 1은 **`platform` / `common` 골격만** 생성한다. `identity` / `authorization` 컨텍스트 디렉터리는 각 Phase(2·4)에서 생성한다 — 빈 디렉터리 선점유 금지. 패키지 계층 규약(`<context>/{domain,application,infrastructure,interfaces}`)은 platform/common에 동일하게 적용해 후속 컨텍스트의 형판으로 삼는다.

### 영속성 검증 (SC#4)
- **D-04:** JPA(쓰기)와 MyBatis(복잡 조회)가 **동일 DataSource·단일 트랜잭션**에서 동작함을 Testcontainers 통합 테스트로 증명하고, 그 검증 코드는 **유지**한다(회귀 방지). 검증 슬라이스의 정확한 위치(platform 내 샘플 vs 별도)는 planner 재량.

### 의존 방향 강제 (SC#2)
- **D-05:** **ArchUnit 테스트**로 `interfaces → application → domain` 의존 방향과 `infrastructure`의 포트 구현 규칙을 자동 강제하고 CI 게이트로 둔다. Spring Modulith는 채택하지 않음(추가 의존성·학습 비용 대비 Phase 1 골격에 과함; 향후 도메인 이벤트 단계에서 재검토 가능 — Deferred 참조).

### 로컬 인프라 & 헬스체크 (SC#5)
- **D-06:** 로컬 개발은 **Docker Compose**(PostgreSQL + Redis)로 기동한다. 자동화된 SC#4·SC#5 검증은 **Testcontainers** 통합 테스트로 수행한다(두 경로 병행). 헬스체크는 `/actuator/health`가 앱·DB(PostgreSQL)·Redis 상태를 모두 반환하도록 구성한다.

### Claude's Discretion
- Gradle 플러그인/버전 카탈로그 구성, Flyway 마이그레이션 파일 네이밍/디렉터리, ArchUnit 규칙 세부 표현, Testcontainers 검증 슬라이스의 정확한 패키지 위치, `actuator` health indicator 노출 범위 세부 — 모두 planner/researcher 재량(기준 문서·SC 위반 없는 선에서).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 정본 표준 (단일 진실 공급원)
- `spring-backend-ddd-baseline.md` (문서 v1.0) — 이 프로젝트 전체의 정본. Phase 1 관련 핵심 절:
  - §2 기술 스택 — Java 21 / Spring Boot MVC / Spring Data JPA + MyBatis / PostgreSQL / Redis / Flyway / Lombok (임의 대체 금지). "동일 DataSource로 단일 트랜잭션에 묶는다."
  - §3 A-1 (MVC + 가상 스레드, WebFlux 아님), A-2 (JPA+MyBatis 병행)
  - §4.2 패키지/계층 구조 (헥사고날+DDD, 의존 방향 안쪽) — 루트 패키지는 D-02로 대체
  - §4.4 JPA 방식 A(엔티티=애그리거트) 기본
  - §4.5 MyBatis = 읽기 모델 (CQRS-lite)
  - §4.6 트랜잭션·정합성 경계 (1 트랜잭션 = 1 애그리거트)
  - §6 NFR — NFR-04(가상 스레드), NFR-06(Flyway 스키마 관리)

### 단계 정의 & 요구사항
- `.planning/ROADMAP.md` — Phase 1 정의, Goal, Success Criteria 1~5
- `.planning/REQUIREMENTS.md` — PLAT-01 ~ PLAT-06
- `.planning/PROJECT.md` — Key Decisions(JPA 방식 A, 인가 내부 어댑터 우선), Constraints

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- 없음 — greenfield. 소스 코드, `build.gradle`/`pom.xml`, `.planning/codebase/` 맵이 아직 존재하지 않는다. 직전 인증 중심 프로젝트는 삭제됨(PROJECT.md Context).

### Established Patterns
- 코드 패턴은 아직 없으나, 기준 문서 §4.2 패키지 규약이 이 Phase에서 처음으로 코드화된다 — 이후 모든 컨텍스트의 형판이 된다.

### Integration Points
- 없음 — 이 Phase가 후속 컨텍스트의 통합 지점(패키지 골격, DataSource, Redis, 헬스체크)을 처음 만든다.

</code_context>

<specifics>
## Specific Ideas

- 골격은 "복제해서 업무 도메인만 채우는" 재사용 템플릿이 목표(PROJECT.md Core Value). 따라서 Phase 1 산출물은 후속 프로젝트가 그대로 가져갈 수 있을 만큼 규약이 명확해야 한다.

</specifics>

<deferred>
## Deferred Ideas

- **Spring Modulith** — 모듈 경계 강제 + 도메인 이벤트 인프라 제공. 컨텍스트 간 도메인 이벤트(예: `UserDisabled` → Authorization 구독, §4.6)가 실제로 필요해지는 Phase 2~4 시점에 ArchUnit 대체/보완 여부 재검토. Phase 1에서는 채택하지 않음.

</deferred>

---

*Phase: 1-platform-skeleton*
*Context gathered: 2026-05-30*
