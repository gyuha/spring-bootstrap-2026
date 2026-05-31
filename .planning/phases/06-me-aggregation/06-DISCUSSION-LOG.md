# Phase 6: 내 정보 신원·권한 집계 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.
>
> **수집 방식:** 비대화형(autonomous subagent) 세션. AskUserQuestion 불가하여 각 그레이 영역을
> 코드 실측·ROADMAP/REQUIREMENTS/PROJECT·lesson 근거로 자율 결정했다. 아래 표의 "Selected"는
> 자율 결정한 선택지다.

**Date:** 2026-05-31
**Phase:** 6-me-aggregation
**Areas discussed:** Identity 신원 조회, AUTH-09 권한 집계 범위, me() 응답 DTO, ArchUnit 정비(AUTH-11), 검증 전략

---

## Identity 신원 조회 (AUTH-08)

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 메서드 재사용 | IdentityApplicationService에 read 메서드가 있으면 재사용 | (불가 — read 메서드 부재 실측) |
| 신규 application read + DTO 반환 | 신규 read 메서드 추가, User 엔티티 대신 평탄 DTO 반환 | ✓ |
| interfaces가 UserRepository.findById 직접 호출 | interfaces→domain 직접 | (헥사고날 누수로 기각) |

**선택:** 신규 application read 메서드 + 읽기 DTO. (D-01)
**근거:** `IdentityApplicationService`는 invite/linkIdentity/disable 쓰기 3종뿐(read 0건, 실측). User 엔티티 노출은 JPA 누수 → application이 DTO로 캡슐화.

---

## AUTH-09 권한 집계 범위 (핵심 결정, 신뢰도 중간)

| Option | Description | Selected |
|--------|-------------|----------|
| 직접 부여(direct)만 | GlobalRole/Menu/Resource findByUserId 3종 조율. 상속 제외 | ✓ |
| direct + 그룹 상속 | GroupMember.findByUserId로 그룹 grant 합산 | |
| 완전 해소 effective | 신규 "user 전체 effective grants" 재귀 CTE 읽기 모델 신설 | |

**선택:** 직접 부여(direct)만. (D-02)
**근거:** 검증된 `findByUserId` 3종 재사용·신규 CTE 불필요. 기존 재귀 CTE(PermissionReadPort)는 리소스/액션 단위라 사용자 전체 권한 열거에 부적합. ROADMAP SC#2 3버킷 명시와 정합. §5.1 과집계 금지.
**Notes:** SPA 메뉴/라우트 가드가 그룹/계층 상속 권한까지 필요하면 부족 — planner/사용자가 SPA 요구와 맞는지 우선 확인. baseline 최소 표면을 기본값으로 고정.

---

## me() 응답 DTO

| Option | Description | Selected |
|--------|-------------|----------|
| 단일 record MeResponse(신원+권한 결합) | Phase 5 record+@JsonInclude 계승, 빈 컬렉션 [] | ✓ |
| 두 개의 분리 응답/엔드포인트 | 신원·권한 분리 | (ROADMAP "단일 응답"·PROJECT "의도적 결합"으로 기각) |
| Map 반환 | MeController 스타일 | (Map.of null NPE lesson으로 기각) |

**선택:** 단일 record `MeResponse`. (D-03)
**근거:** ROADMAP SC#2 단일 응답·PROJECT.md 의도적 결합. lesson 05 record+@JsonInclude(Map.of NPE 회피). 빈 컬렉션은 `[]`, nullable 스칼라만 NON_NULL.

---

## ArchUnit 정비 (AUTH-11, 신뢰도 중간)

| Option | Description | Selected |
|--------|-------------|----------|
| 무변경 + GREEN 단언 | 현재 전역 레이어 글로브가 교차 컨텍스트 application 호출 이미 허용 | ✓ (권장) |
| 컨텍스트 인지 슬라이스 규칙 신설 | auth→identity/authorization application 명시 허용을 slices()로 표현 | (baseline 광역 영향 — planner 검토 여지) |

**선택:** 무변경으로 GREEN(권장). (D-06)
**근거:** `ArchitectureTest`는 단일 layeredArchitecture 규칙, 레이어를 접미 글로브(컨텍스트 무관 전역)로 정의 → `auth/interfaces → identity/authorization application`은 `Interfaces→Application`으로 이미 허용(실측, 게이트 1개뿐). `IdentityLinkService`(auth.application→identity.application)가 통과 중인 선례. 슬라이스 신설은 4 컨텍스트 광역 영향 + 누수 사각(lesson 04).
**Notes:** "명시성" 갭 존재 — 현재 규칙은 컨텍스트를 구분 못 해 "우연히 통과"다. AUTH-11이 "ArchUnit이 교차 의존을 *인지·문서화*"를 요구하면 슬라이스 규칙(option b) 검토. planner/사용자가 강도 확인.

---

## 검증 전략 (SC#1~3)

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers+WireMock+MockMvc, oidcLogin 우회 금지 | 실 User+실 grant 시드, 3자 일치 | ✓ |
| oidcLogin 합성 claim만 | 빠르나 실 User/grant 미연결 | (집계 거짓 통과 위험으로 기각) |
| 라이브 구동 | 실 IdP/포트 | (포트 혼잡 8080/5432/6379로 기각) |

**선택:** Testcontainers+WireMock+MockMvc, oidcLogin 우회 금지. (D-07)
**근거:** lesson 03/04/05 — 포트 혼잡, oidcLogin 우회 금지, 실 Postgres. principal user_id ↔ 시드 User.id ↔ grant.userId 3자 일치 필수(합성 claim은 거짓 통과).

---

## Claude's Discretion

- read 메서드 시그니처·이름, read DTO 형태(record vs class)·필드명, MeResponse 중첩 구조, me() 시그니처, userId 타입 변환, readOnly 트랜잭션 위치, 신규 IT 분리 vs 확장, 미존재 User HTTP 매핑(404 vs 500) — 정본·SC·게이트·NFR 위반 없는 선에서 재량.
- 단 D-02(집계 범위)·D-06(ArchUnit 명시성 강도)는 재량 아닌 planner/사용자 확인 사항.

## Deferred Ideas

- 그룹/리소스 계층 상속을 me() 권한에 포함(D-02 확장 시)
- 컨텍스트 인지 ArchUnit 슬라이스 규칙(D-06 option b)
- `/api/me`(MeController) 정리/deprecate — Phase 5에서 이연. 제거 시 Phase 3/5 테스트 회귀 → 공존 유지 권장
- 인가 권한 변경 REST API(Out of Scope)
- 권한 응답 캐싱·ETag·변경 시 무효화
