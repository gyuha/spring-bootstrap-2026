# Phase 1: 플랫폼 골격 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-30
**Phase:** 1-platform-skeleton
**Areas discussed:** 빌드/모듈 구조, 루트 패키지 & 컨텍스트 선구성, 의존 방향 강제(SC#2), 로컬 인프라 & 검증(SC#4·5)

---

## 빌드 도구 & 모듈 구조

| Option | Description | Selected |
|--------|-------------|----------|
| Gradle(Kotlin DSL) 단일 모듈 | 골격 단순성 우선, 컨텍스트는 패키지로 분리, 의존 방향은 ArchUnit으로 강제 | ✓ |
| Maven 단일 모듈 | 사내 표준이 Maven이면 선택 | |
| Gradle 멀티모듈(컨텍스트별) | 컴파일 타임 강제하나 구성 부담·복잡도 증가 | |

**User's choice:** Gradle(Kotlin DSL) 단일 모듈
**Notes:** 멀티모듈의 컴파일 타임 의존 강제 이점은 ArchUnit 테스트로 대체.

---

## 루트 패키지

| Option | Description | Selected |
|--------|-------------|----------|
| com.anchorsbiz.baseline | anchors-biz 조직 기준, 무난한 자바 관례 | |
| biz.anchors.baseline | anchors-biz 도메인 역순 표기 | |
| com.anchors.baseline | 짧은 형태 | ✓ |

**User's choice:** com.anchors.baseline
**Notes:** 기준 문서 §4.2의 `com.company.app` 플레이스홀더를 대체.

---

## 컨텍스트 디렉터리 선구성

| Option | Description | Selected |
|--------|-------------|----------|
| platform/common 골격만 | Phase 1은 순수 인프라, identity/authorization은 각 Phase에서 생성 | ✓ |
| identity/authorization 빈 선생성 | 세 컨텍스트 4계층 디렉터리를 Phase 1에서 미리 생성 | |

**User's choice:** platform/common 골격만
**Notes:** 빈 디렉터리 선점유 회피. 패키지 계층 규약은 platform/common에 적용해 후속 컨텍스트 형판으로 삼음.

---

## 의존 방향 강제 & 로컬 인프라/검증

| Option | Description | Selected |
|--------|-------------|----------|
| ArchUnit + Compose & Testcontainers | 의존 방향=ArchUnit CI 게이트, 로컬=Compose, SC#4·5 검증=Testcontainers 통합 테스트(유지) | ✓ |
| Spring Modulith + Testcontainers | 모듈 경계를 Modulith로 강제(향후 도메인 이벤트에 유리), 추가 의존성·학습 비용 | |
| 관례+리뷰 + Compose만 | 자동 강제 도구 없음, 검증도 수동 — 골격에는 강제력 부족 | |

**User's choice:** ArchUnit + Compose & Testcontainers
**Notes:** Spring Modulith는 Phase 2~4 도메인 이벤트 도입 시 재검토(Deferred로 보존).

---

## Claude's Discretion

- Gradle 플러그인/버전 카탈로그 구성
- Flyway 마이그레이션 파일 네이밍/디렉터리
- ArchUnit 규칙 세부 표현
- Testcontainers 검증 슬라이스의 정확한 패키지 위치
- `/actuator/health` health indicator 노출 범위 세부

## Deferred Ideas

- **Spring Modulith** — 컨텍스트 간 도메인 이벤트가 실제 필요해지는 Phase 2~4 시점에 ArchUnit 대체/보완 여부 재검토.
