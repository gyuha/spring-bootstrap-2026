# Phase 4: Authorization 컨텍스트 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-31
**Phase:** 4-authorization
**Areas discussed:** 비대화형(autonomous) 세션 — 각 그레이 영역을 ROADMAP SC·AUTHZ-01~09·정본 §5.4·Phase 1~3 코드/게이트/lesson에 근거해 직접 결정

---

> **수집 방식:** 이 세션은 비대화형으로 수행되었다. 아래는 검토된 그레이 영역과 채택/비채택 대안의 감사 기록이다.
> 확정 결정은 CONTEXT.md의 D-01~D-11에 있다.

## `PermissionEvaluator` ↔ Spring Security 통합

| Option | Description | Selected |
|--------|-------------|----------|
| 순수 도메인 서비스 `evaluate(userId, resource, action)` + (선택) 인프라 어댑터 | 정본 §5.4 시그니처 사수, Spring `PermissionEvaluator` 인터페이스는 infrastructure 어댑터로만 위임 | ✓ |
| 도메인 서비스가 Spring `PermissionEvaluator(Authentication, target, permission)` 직접 구현 | MethodSecurity 직결이나 도메인이 framework 타입에 오염(A-4 분리 위반, ArchUnit domain→framework) | |

**채택:** 순수 도메인 서비스(D-03). Spring Security MethodSecurity 어댑터 포함 여부는 SC가 요구하지 않으므로 planner가 골격 완성도 대비 결정.
**Notes:** 동명 인터페이스 충돌이 이 Phase 최대 함정 — 정본 시그니처 `evaluate(userId, resource, action)`를 SC#1·#2가 검증하므로 이를 비틀면 안 됨.

## `AuthorizationPort` 경계 (무엇이 포트 뒤에 있는가)

| Option | Description | Selected |
|--------|-------------|----------|
| 포트 = 판정·조회(evaluate/listObjects)만, 부여/회수는 내부 애그리거트(JPA) | 정본 §5.4 교체 동기는 "판정 엔진"(CTE↔OpenFGA) — 내부 어댑터 우선(PROJECT Key Decision) | ✓ |
| 포트 = 판정+쓰기(write tuple) 모두 | OpenFGA가 쓰기까지 흡수하는 외부 엔진 우선 설계 — "내부 어댑터 우선" 원칙과 불일치 | |

**채택:** 판정·조회 중심 포트(D-04). 최소 연산은 evaluate/listObjects, 부여/회수 포트 포함 여부는 planner가 SC#5 충족 최소 인터페이스로 확정.

## evaluate() 합산 + 계층 상속 구현 (JPA vs MyBatis 재귀 CTE)

| Option | Description | Selected |
|--------|-------------|----------|
| 상속·합산 조회 = MyBatis 재귀 CTE(읽기 모델), 판정 규칙 = 도메인 서비스 | 정본 §4.5·§5.4 명시(계층 상속 재귀 CTE, ListObjects 읽기 측) + Phase 1 SampleQuery/SampleMapper 형판 | ✓ |
| JPA로 리소스 트리 재귀 로드 | 애그리거트 복원 비용·N+1, 정본이 명시적으로 읽기 측으로 분리 | |

**채택:** MyBatis 재귀 CTE 읽기 모델(D-05). 읽기 포트=application, `@Mapper`=infrastructure(게이트 회피 형판).
**Notes:** 역할 함의(EDITOR ⊇ VIEW)는 도메인 서비스에 캡슐화. 함의 매핑 자료구조는 planner 재량.

## 즉시 반영 메커니즘 (SC#1)

| Option | Description | Selected |
|--------|-------------|----------|
| 매 evaluate 호출 시 DB 조회(캐시 없음) | 즉시성 무비용 보장, NFR-01 부합, 무효화 복잡도 회피 | ✓ |
| 권한 평가 캐시 + 무효화 | 미검증 최적화, 무효화 복잡도가 SC#1 위협(lesson 03 교훈) | |

**채택:** 캐시 없음(D-06). 성능 트립와이어(A-7) 도달 시 캐시 재검토(Deferred).

## 미로그인(INVITED) 권한 — 크로스 컨텍스트 참조 (FK 정책)

| Option | Description | Selected |
|--------|-------------|----------|
| `user_id` soft reference, identity `users`로 물리 FK 없음 | 정본 §4.1 컨텍스트 독립(ID 참조), 외부 엔진/별 DB 교체 대비, SC#4 추가 코드 없이 충족 | ✓ |
| identity `users.id`로 물리 FK | 단일 DB 운영 무결성이나 두 컨텍스트 스키마 결합 | |

**채택:** FK 없음·soft ref(D-07). planner가 단일 DB 무결성 트레이드오프 재검토 가능하나 베이스라인 권장은 FK 없음.
**Notes:** ACTIVE 전이는 Identity 내부 상태 변화일 뿐 Authorization 데이터에 무영향 → SC#4가 추가 코드 없이 충족.

## 영속성 — created_at 이중 소스 (lesson 02 P2)

| Option | Description | Selected |
|--------|-------------|----------|
| 신규 엔티티도 기존 이중 소스(엔티티 Instant.now() + DDL default) 따름 | 베이스라인 일관성 | (planner 결정) |
| 새 컨텍스트에서 단일화(엔티티 소유 vs DB default 택1) | lesson 02 P2 미해소 패턴의 정리 시작점 | (planner 결정) |

**채택:** planner 명시 결정으로 위임(D-09 [가정]). 새 컨텍스트라 단일화 적용 시작점일 수 있음.

## Claude's Discretion
- 애그리거트별 VO·필드 표현, 역할 함의 매핑 자료구조, MyBatis 매퍼 표현(`@Select` vs XML), 주체(user/group) 권한 테이블 구조(polymorphic vs 분리), evaluate 결과 타입, Spring Security 어댑터 포함 여부, wave 분할, 예외 타입 계층 — 정본 §5.4·§4.5·SC·ArchUnit·NFR 위반 없는 선에서 재량(CONTEXT.md Claude's Discretion 참조).

## Deferred Ideas
- 외부 인가 엔진(OpenFGA) 어댑터 + ACL + dual-write (A-7 트립와이어 전 보류)
- 권한 평가 캐시 + 무효화 (성능 실측 문제 시)
- `UserDisabled` 이벤트 구독 → 권한 일괄 정리 (SC 미요구)
- Spring Security MethodSecurity 어댑터 (SC 미요구, 골격 완성도용 선택)
- REST 관리자 API (프론트/UI 범위 밖)
- `UserActivated` 이벤트 기반 권한 활성화 (불필요 — 권한은 INVITED부터 저장)
- `created_at` 이중 소스 베이스라인 전체 단일화 (별도 정리 태스크)
