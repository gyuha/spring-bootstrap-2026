# Phase 2: Identity 컨텍스트 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.
>
> **세션 성격:** 비대화형(autonomous) 실행. 사용자 대화 없이 ROADMAP SC#1~5 · IDEN-01~06 ·
> 정본 표준(§4.4·§4.6·§5.2) · Phase 1 코드/ArchUnit 게이트에 근거해 결정. 아래 표의
> "Selected"는 그 근거로 도출된 선택이며, **[가정]** 항목은 planner가 사용자에게
> 표면화해야 한다.

**Date:** 2026-05-31
**Phase:** 02-identity-context
**Areas discussed:** JPA 매핑 방식, VO 매핑, 도메인 이벤트 메커니즘, 불변식 강제 위치, 유일성/매칭, 인터페이스 노출 범위

---

## JPA 매핑 방식 (User 애그리거트)

| Option | Description | Selected |
|--------|-------------|----------|
| 방식 A (실용형) | JPA 엔티티 = 애그리거트. setter 금지·정적 팩토리·protected 무인자 생성자 | ✓ |
| 방식 B (엄격 분리) | 순수 도메인 + 별도 JPA 영속성 모델, infra에서 변환 | |

**근거:** PROJECT.md Key Decision(방식 A 기본) + Phase 1 `SampleEntity` 패턴 계승 + 골격 재사용성(이중 모델 부담 회피). 정본 §4.4 권장.

---

## 값 객체(VO) 매핑

| Option | Description | Selected |
|--------|-------------|----------|
| @Embeddable VO + STRING enum | Email/ExternalId = @Embeddable, UserStatus = @Enumerated(STRING) | ✓ |
| 원시 String/ordinal | 원시 타입 직접 매핑 | |

**근거:** 정본 §4.3·§5.2가 VO 명시. ordinal 대신 STRING(스키마 안정성). **[가정]** 단일 컬럼 평탄화는 planner가 `@AttributeOverride` 필요 여부 확정.

---

## 도메인 이벤트 발행 메커니즘 (UserDisabled)

| Option | Description | Selected |
|--------|-------------|----------|
| Spring AbstractAggregateRoot + @DomainEvents | save() 시점 발행, 트랜잭션 커밋 정합. 신규 의존성 0 | ✓ |
| Spring Modulith | 모듈 경계 + 이벤트 인프라 | |
| 외부 메시지 브로커 | Kafka/Rabbit 등 | |

**근거:** Phase 1에 이벤트 인프라 전무(grep 확인). NFR-01(외부 의존 최소화) + §4.6(1트랜잭션=1애그리거트). Phase 2는 발행 측만 필요. **[가정]** `UserActivated`는 SC 미요구 → 미발행(Deferred).

---

## 불변식 강제 위치 (전이·멱등성·필드 소유권)

| Option | Description | Selected |
|--------|-------------|----------|
| 애그리거트 메서드 내부 | 전이 가드·멱등성·소유권 모두 User 메서드에. 메서드 시그니처로 소유권 강제 | ✓ |
| 애플리케이션 서비스 | 서비스 레이어에서 규칙 검사 | |

**근거:** 정본 §4.3 — 애플리케이션 서비스에 비즈니스 규칙 금지. IDEN-03 멱등성·IDEN-06 소유권을 도메인에 캡슐화. 도메인 예외 타입 사용(원시 예외 지양).

---

## 유일성 제약 & 로그인 후 매칭

| Option | Description | Selected |
|--------|-------------|----------|
| DB UNIQUE + 도메인 선검사 병행 | email UNIQUE(최종 방어선) + 앱 선검사(친화적 예외), external_id nullable+UNIQUE | ✓ |
| DB 제약만 | DB UNIQUE 단독 | |
| 도메인 검사만 | 동시성 취약 | |

**근거:** IDEN-05 + A-6. 매칭은 로컬 PK(User.id)로만 — Phase 3/4 계약으로 고정.

---

## 인터페이스 노출 범위 (REST 컨트롤러 포함 여부)

| Option | Description | Selected |
|--------|-------------|----------|
| domain+application+infra+test (컨트롤러 없음) | SC가 메서드 호출 수준만 요구. 외부 진입점은 Phase 3 이후 | ✓ |
| REST 컨트롤러 포함 | invite/disable HTTP 엔드포인트 추가 | |

**근거:** ROADMAP SC#1~5가 전부 메서드 호출 수준. 인증 없는 노출은 보안·미사용 표면. **[가정]** 검증은 Testcontainers 통합 + 도메인 단위 테스트.

---

## Claude's Discretion

- 도메인 예외 클래스 계층·패키지, Email 검증 정규식 수준, UserStatus enum 표현, @Embeddable 컬럼 평탄화 세부, AbstractAggregateRoot vs 명시적 @DomainEvents, 트랜잭션 경계 표현, Flyway 네이밍(V2__...), 테스트 슬라이스 구성.

## Deferred Ideas

- `UserActivated` 이벤트 — SC 미요구, 구독자 부재 → Phase 3/4에서 재검토
- REST 컨트롤러 + 관리자 invite API — Phase 3 이후 또는 별도 admin API Phase
- MyBatis 읽기 모델(사용자 목록) — 복잡 조회 요구 발생 시
- Spring Modulith — Phase 4(다수 구독자) 시점 재검토
