# Phase 2: Identity 컨텍스트 - Research

**Researched:** 2026-05-31
**Domain:** Spring DDD 애그리거트(JPA 방식 A) · 도메인 이벤트 발행 · 값 객체 매핑 · Flyway 스키마 · Testcontainers 검증
**Confidence:** HIGH (정본·CONTEXT 결정·Phase 1 코드가 설계를 거의 완결적으로 고정 / 검증 가능한 외부 사실은 Spring Data·Hibernate 공식 문서로 교차 확인)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 ~ D-07 — 그대로 준수, 대안 탐색 금지)

- **D-01 JPA 매핑 방식 A(실용형):** `User` 애그리거트 = JPA 엔티티 루트. `@Entity`를 `identity/domain/model`에 직접. `protected` 무인자 생성자, public setter 금지, 정적 팩토리/생성자로만 생성, 상태 변경은 의미 메서드(`invite`/`linkIdentity`/`disable`)로만. 방식 B 미채택.
- **D-02 VO 매핑:** `Email`·`ExternalId` = `@Embeddable` 값 객체, `UserStatus` = `@Enumerated(EnumType.STRING)`. `Email`은 생성 시점 형식 검증, `ExternalId`는 불변·nullable(초대 시 null, 연결 후 set). 원시 `String` 누출 금지. **[가정]** 단일 컬럼 평탄화(`email`, `external_id`) — `@AttributeOverride` 필요 여부 planner 확정.
- **D-03 도메인 이벤트 메커니즘:** `UserDisabled`는 Spring Data `AbstractAggregateRoot` + `@DomainEvents` 패턴으로, 리포지토리 `save()` 시점 발행. Spring Modulith·외부 브로커 미도입. **[가정]** 이벤트 위치 = `identity/domain/event/UserDisabled`, 페이로드 = `userId`(로컬 PK)만. `UserActivated`는 SC 미요구 → 미발행.
- **D-04 불변식 강제 위치:** 모든 규칙은 `User` 메서드 내부. `linkIdentity`는 `INVITED`에서만, 이미 `ACTIVE`면 예외(IDEN-03). `disable`은 임의 상태→`DISABLED`(**[가정]** 이미 DISABLED 시 무연산 vs 예외 — planner 확정). 필드 소유권(IDEN-06)은 `linkIdentity(externalId, idpName)`가 관리자 입력 필드를 **인자로 받지 않아 구조적으로** 덮어쓰기 불가.
- **D-05 도메인 예외:** `common` 또는 `identity/domain`의 도메인 예외 타입(원시 `IllegalStateException` 남발 금지). 멱등성 위반·잘못된 전이를 구분 가능한 타입. 정확한 패키지/계층은 planner 재량.
- **D-06 유일성·매칭:** email = DB UNIQUE + 앱 레벨 선검사 병행. `external_id` = nullable + UNIQUE. 로그인 후 매칭 = `User.id`(로컬 PK)로만(A-6).
- **D-07 산출물 범위:** domain + application + infrastructure(JPA 어댑터 + Flyway) + 단위/통합 테스트. **REST 컨트롤러(interfaces) 미생성.** 검증은 Testcontainers 통합 테스트 + 도메인 순수 단위 테스트로 SC#1~5 증명. Phase 1 `AbstractIntegrationTest` 재사용.

### Claude's Discretion (planner/researcher 재량)
도메인 예외 클래스 계층·정확한 패키지(`common` vs `identity/domain`), `Email` 검증 정규식 수준, `UserStatus` enum 값 표현, `@Embeddable` 컬럼 평탄화 세부(`@AttributeOverride`), `AbstractAggregateRoot` vs 명시적 `@DomainEvents` 메서드, 애플리케이션 서비스 트랜잭션 경계 표현, Flyway 파일 네이밍(`V2__...`), 테스트 슬라이스 구성 — 정본·SC·ArchUnit 게이트 위반 없는 선에서.

### Deferred Ideas (OUT OF SCOPE — 무시)
- `UserActivated` 이벤트 (Phase 3/4 필요 시)
- REST 컨트롤러 + 관리자 invite API (Phase 3 이후)
- MyBatis 읽기 모델 (복잡 조회 요구 발생 시)
- Spring Modulith (Phase 4 재검토)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| IDEN-01 | 관리자가 미로그인 사용자를 이메일로 초대 → INVITED | `User.invite(Email)` 정적 팩토리 + `users` 테이블 + 통합 테스트 §"Testing" 시나리오 1 |
| IDEN-02 | 최초 로그인 시 externalId 연결 → INVITED→ACTIVE | `User.linkIdentity(ExternalId, idpName)` 상태 전이 가드 §"State Machine" |
| IDEN-03 | 동일 신원 두 번 연결 거부 | `linkIdentity` 멱등성 가드 (status≠INVITED 또는 externalId!=null → 도메인 예외) §"State Machine" |
| IDEN-04 | disable → DISABLED + `UserDisabled` 이벤트 | `AbstractAggregateRoot.registerEvent` + save() 발행 §"Domain Events" |
| IDEN-05 | email 유일 + 로그인 후 매칭 = 로컬 PK | DB UNIQUE(`email`) + `findByExternalId`는 연결용일 뿐, 업무 참조는 `User.id` §"Uniqueness" |
| IDEN-06 | IdP 필드는 갱신, 관리자 필드는 보존 | `linkIdentity` 시그니처가 관리자 필드 미수령(구조적 강제) §"State Machine" |
</phase_requirements>

## Summary

이 Phase는 **새로운 사실을 거의 요구하지 않는다.** 설계는 정본 §5.2가 worked example로 기술하고, CONTEXT D-01~D-07이 7개 그레이 영역을 모두 잠갔으며, Phase 1 코드(`SampleEntity`·`SampleRepository`·`SampleJpaRepository`·`AbstractIntegrationTest`)가 형판을 제공한다. 연구 가치는 두 곳에 집중된다: (1) **Spring Data `AbstractAggregateRoot`의 발행 타이밍·`@Transient` 요구·트랜잭션 경계 정합성**의 버전 정확한 확인, (2) **Hibernate 6의 "모든 컬럼 NULL인 `@Embeddable`은 객체 자체가 null로 인스턴스화된다"** 함정 — 이것이 nullable `ExternalId` VO 설계를 직접 좌우한다.

해석 versions(빌드에서 실측): Spring Boot 3.5.3 / Spring Data JPA 3.5.1 / Spring Data Commons 3.5.1 / Hibernate ORM 6.6.18.Final / Spring Framework 6.2.8 / Java 21. `[VERIFIED: ./gradlew dependencies]`

**Primary recommendation:** 단일 `User` 애그리거트(`@Entity`, `extends AbstractAggregateRoot<User>`)에 `Email`·`ExternalId` `@Embeddable` VO와 `UserStatus` STRING enum을 매핑한다. `disable()`에서 `registerEvent(new UserDisabled(this.id))` 호출 → `UserRepository.save()`가 트랜잭션 내(커밋 전) 발행. `ExternalId`는 **all-null=null 함정을 피하기 위해 `String` 컬럼으로 직접 매핑하거나, `@Embeddable`로 둘 경우 nullable 의미를 명시 검증**한다. MyBatis 읽기 모델은 이 Phase에서 **만들지 않는다**(D-07·SC가 메서드 호출만 검증). interfaces 계층도 미생성(D-07).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| invite/linkIdentity/disable 불변식 | Domain (`User` 애그리거트) | — | 정본 §4.3 "규칙은 도메인에". 애플리케이션 서비스 규칙 금지(D-04) |
| 상태 전이 가드·멱등성 | Domain (`User` 메서드) | — | IDEN-02/03 — 애그리거트가 자신의 상태 머신 소유 |
| `UserDisabled` 발행 | Domain (애그리거트가 `registerEvent`) | Infrastructure (`save()`가 트리거) | `AbstractAggregateRoot`는 domain, 발행 트리거는 JPA 리포지토리 |
| email 유일성 선검사 | Application (조율) | Domain (선검사 호출), Infrastructure (DB UNIQUE 최종 방어) | 선검사=UX, DB 제약=동시성 최종 방어(D-06) |
| 영속화 | Domain (포트 `UserRepository`) | Infrastructure (`UserJpaRepository` 어댑터) | Phase 1 포트/어댑터 형판 |
| 스키마(`users` 테이블) | Infrastructure (Flyway `V2__`) | — | NFR-06 |
| 유스케이스 조율·트랜잭션 경계 | Application (`@Service @Transactional`) | — | 정본 §4.3 — 비즈니스 규칙 없이 조율만 |

**주의:** Phase 1 01-04 자기모순(읽기 DTO/포트를 infra에 두려다 application→infra 강제)을 반복하지 않는다. 이 Phase의 모든 포트(`UserRepository`)는 domain에, 어댑터(`UserJpaRepository`)는 infrastructure에. 읽기 DTO/쿼리 포트가 필요해지면 application에 둔다(이번 Phase는 불필요).

## Standard Stack

이 Phase는 **새 의존성을 추가하지 않는다.** 필요한 모든 라이브러리는 Phase 1에서 이미 BOM으로 고정되어 있다. `AbstractAggregateRoot`는 `spring-data-commons`(이미 `spring-boot-starter-data-jpa` 추이 의존)에 포함된다.

### Core (이미 설치됨 — 버전 실측)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-data-jpa | 3.5.3 (BOM) | JPA 애그리거트 영속화 | 정본 2장 고정 |
| spring-data-jpa | 3.5.1 | `JpaRepository`, 도메인 이벤트 발행 인프라 | Phase 1 포트/어댑터 형판 |
| spring-data-commons | 3.5.1 | `AbstractAggregateRoot`, `@DomainEvents`, `@AfterDomainEventPublication` | D-03 메커니즘 제공 |
| hibernate-core | 6.6.18.Final | `@Embeddable` VO·`@Enumerated` 매핑 | spring-data-jpa 추이 의존 |
| spring-context | 6.2.8 | `ApplicationEventPublisher`(Spring Data가 내부 사용) | — |
| flyway-core + flyway-database-postgresql | 11.x (BOM) | `V2__` 스키마 마이그레이션 | NFR-06, Phase 1 형판 |
| lombok | 1.18.38 | `@Getter` (setter 금지) | 정본 2장 |

**`[VERIFIED: ./gradlew dependencies --configuration runtimeClasspath]`** — 위 4개 핵심 버전(spring-data-jpa 3.5.1, spring-data-commons 3.5.1, hibernate-core 6.6.18.Final, spring-context 6.2.8) 실측 확인.

### Alternatives Considered (D-03이 이미 선택 — 참고용)
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `AbstractAggregateRoot` | `ApplicationEventPublisher` 직접 주입 | 애그리거트에 publisher 주입 = 도메인 오염. AbstractAggregateRoot가 save() 정합성 + 무인프라로 우월. **D-03이 AbstractAggregateRoot 채택.** |
| `AbstractAggregateRoot` | 명시적 `@DomainEvents`/`@AfterDomainEventPublication` 메서드 | 베이스클래스 상속 불가 시(다중 상속 충돌) 명시 메서드. `User`는 상속 충돌 없음 → 베이스클래스가 보일러플레이트 적음. **D-03/Discretion이 AbstractAggregateRoot 우선.** |

**Installation:** 없음 — 신규 의존성 0건.

## Package Legitimacy Audit

> 이 Phase는 **외부 패키지를 설치하지 않는다.** 모든 라이브러리는 Phase 1에서 검증·고정된 Spring Boot 3.5.3 BOM 관리 의존성. 신규 registry 조회·slopcheck 대상 없음.

| Package | Registry | 신규 설치? | Disposition |
|---------|----------|-----------|-------------|
| (없음) | — | No | N/A — Phase 1 BOM 재사용 |

## Architecture Patterns

### System Architecture Diagram

invite/disable는 관리자 유스케이스(현재 외부 진입점 없음 — Phase 3 이후 admin API), linkIdentity는 Phase 3 인증 흐름이 호출한다. Phase 2는 이 진입점들이 호출할 **도메인 연산과 영속화·이벤트 발행 슬라이스**만 만든다. 데이터 흐름:

```
[Command: invite]
호출자(테스트/미래 admin API)
  → IdentityApplicationService.invite(email)   @Transactional
      → email 선검사(UserRepository.existsByEmail)  ─실패→ EmailAlreadyExists 예외
      → User.invite(Email)  [정적 팩토리, status=INVITED]
      → UserRepository.save(user)
          → UserJpaRepository (JpaRepository 어댑터)
              → INSERT users (email UNIQUE 최종 방어)

[Command: linkIdentity]   (Phase 3가 호출)
  → IdentityApplicationService.linkIdentity(userId, externalId, idpName)  @Transactional
      → User.linkIdentity(ExternalId, idpName)
            ├─ status≠INVITED → InvalidStateTransition 예외 (IDEN-03 멱등성)
            ├─ status=INVITED → externalId set, IdP필드 갱신, status=ACTIVE
            └─ 관리자 입력 필드는 인자에 없어 미변경 (IDEN-06 구조적 강제)
      → UserRepository.save(user)

[Command: disable]
  → IdentityApplicationService.disable(userId)  @Transactional
      → User.disable()
            ├─ status=DISABLED 전이
            └─ registerEvent(new UserDisabled(this.id))   [누적]
      → UserRepository.save(user)
          → @DomainEvents 발행 (트랜잭션 내, 커밋 전, 동기)
          → @AfterDomainEventPublication → 이벤트 목록 clear
          (구독자는 Phase 4 — 이번 Phase는 발행만)
```

파일↔구현 매핑은 아래 Component 표 참조.

### Recommended Project Structure
```
com.anchors.baseline.identity
├── domain
│   ├── model
│   │   ├── User.java              # @Entity, extends AbstractAggregateRoot<User>
│   │   ├── Email.java             # @Embeddable VO, 형식 검증
│   │   ├── ExternalId.java        # @Embeddable VO, 불변 (또는 String 직접매핑 — 함정 §참조)
│   │   └── UserStatus.java        # enum INVITED/ACTIVE/DISABLED
│   ├── event
│   │   └── UserDisabled.java      # 페이로드 = userId(Long) 만
│   ├── repository
│   │   └── UserRepository.java    # 포트 (도메인)
│   └── exception                  # [재량] common vs identity/domain
│       ├── InvalidStateTransition.java
│       └── EmailAlreadyExists.java  # (또는 application 레벨)
├── application
│   └── IdentityApplicationService.java  # @Service @Transactional, 조율만
└── infrastructure
    └── jpa
        └── UserJpaRepository.java # extends JpaRepository<User,Long>, UserRepository
```
**interfaces 계층 미생성(D-07).** Flyway: `src/main/resources/db/migration/V2__create_identity_users.sql`.

### Component Responsibilities
| File | Layer | Responsibility |
|------|-------|----------------|
| `User` | domain/model | 애그리거트 루트. invite/linkIdentity/disable 불변식. 이벤트 누적 |
| `Email`/`ExternalId` | domain/model | 값 객체 — 검증·불변·동등성 |
| `UserStatus` | domain/model | 상태 enum (STRING 매핑) |
| `UserDisabled` | domain/event | 도메인 이벤트 — userId 페이로드 |
| `UserRepository` | domain/repository | 영속화 포트 (save, findById, existsByEmail 등) |
| `IdentityApplicationService` | application | 유스케이스 조율 + 트랜잭션 경계. 규칙 없음 |
| `UserJpaRepository` | infrastructure/jpa | 포트 어댑터. `save()`가 이벤트 발행 트리거 |

### Pattern 1: 애그리거트 루트 + AbstractAggregateRoot (방식 A)
**What:** JPA 엔티티가 애그리거트 루트. 이벤트는 애그리거트가 누적, `save()`가 발행.
**When:** D-01·D-03 — 이 Phase의 기본.
**Example (code-shaped — planner 참고용, 정확한 검증/예외는 재량):**
```java
// Source: 정본 §4.4·§5.2 + Spring Data JPA core-domain-events 문서 + Phase 1 SampleEntity 스타일
package com.anchors.baseline.identity.domain.model;

@Entity
@Table(name = "users")
@Getter
public class User extends AbstractAggregateRoot<User> {   // AbstractAggregateRoot: spring-data-commons

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // → BIGSERIAL (Phase 1 규약)
    private Long id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false, unique = true))
    private Email email;

    // ExternalId: all-null embeddable 함정 회피 — §Common Pitfalls 참조
    @Column(name = "external_id", unique = true)          // nullable (연결 전 null)
    private String externalId;

    @Enumerated(EnumType.STRING)                          // D-02
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "display_name")                        // IdP 출처 필드 (linkIdentity가 갱신)
    private String displayName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}                                   // JPA 전용 (D-01)

    private User(Email email) {                           // 정적 팩토리 경유만
        this.email = email;
        this.status = UserStatus.INVITED;
    }

    public static User invite(Email email) {              // IDEN-01
        return new User(email);
    }

    public void linkIdentity(String externalId, String idpDisplayName) {  // IDEN-02/03/06
        if (this.status != UserStatus.INVITED) {          // 멱등성 가드 (IDEN-03)
            throw new InvalidStateTransition("이미 신원이 연결된 사용자입니다");
        }
        this.externalId = externalId;                     // IdP 출처 — 갱신
        this.displayName = idpDisplayName;                // IdP 출처 — 갱신
        // 관리자 입력 필드는 인자에 없어 구조적으로 미변경 (IDEN-06)
        this.status = UserStatus.ACTIVE;
    }

    public void disable() {                               // IDEN-04
        // [가정] 이미 DISABLED 처리 — planner 확정 (무연산 vs 예외)
        this.status = UserStatus.DISABLED;
        registerEvent(new UserDisabled(this.id));         // save() 시 발행
    }
}
```

### Pattern 2: 도메인 이벤트 발행 (save-on-publish)
**What:** `registerEvent`로 누적 → `UserRepository.save()` 호출 시 Spring Data가 `@DomainEvents` 발행 → `@AfterDomainEventPublication`이 목록 clear.
**핵심 타이밍 (HIGH — 공식 문서 확인):** 발행은 **`save()` 실행 중, 트랜잭션 내부, 커밋 전, 동기적**으로 일어난다. "커밋 후" 시맨틱이 아니다. 구독자(Phase 4)가 커밋 후 처리를 원하면 `@TransactionalEventListener(phase = AFTER_COMMIT)`를 사용해야 한다 — 이는 Phase 4 소관이며, Phase 2는 발행만.
```java
// Source: 정본 §4.6 + CONTEXT D-03
package com.anchors.baseline.identity.domain.event;

public record UserDisabled(Long userId) {}   // 컨텍스트 간 참조는 ID로만 (§4.1)
```

### Pattern 3: 포트/어댑터 (Phase 1 형판 그대로)
```java
// domain/repository — 포트
public interface UserRepository {
    User save(User user);
    Optional<User> findById(Long id);
    boolean existsByEmail(Email email);          // email 선검사용 (D-06)
    Optional<User> findByExternalId(String externalId);  // 연결 중복 선검사용 (선택)
}

// infrastructure/jpa — 어댑터 (Phase 1 SampleJpaRepository 패턴)
public interface UserJpaRepository extends JpaRepository<User, Long>, UserRepository {
    // existsByEmail/findByExternalId는 Spring Data 파생 쿼리로 자동 구현
}
```
**주의:** `existsByEmail(Email)`이 `@Embeddable` 파라미터를 받으면 Spring Data 파생 쿼리가 임베디드 필드로 매핑한다. `@Embeddable` 단일 필드명이 `value`이고 컬럼이 `email`이면 메서드명·매핑이 정합해야 한다. 불확실하면 `existsByEmail_Value(String)` 또는 `@Query`로 명시 — planner가 통합 테스트로 검증.

### Anti-Patterns to Avoid
- **애플리케이션 서비스에 규칙 배치:** 멱등성·전이 가드를 `IdentityApplicationService`에 두면 정본 §4.3 위반. 규칙은 `User` 메서드에만(D-04).
- **`ApplicationEventPublisher`를 `User`에 주입:** 도메인 오염. `AbstractAggregateRoot.registerEvent` 사용(D-03).
- **읽기 DTO/포트를 infrastructure에 배치:** Phase 1 01-04 자기모순 재발 → ArchUnit RED. 이번 Phase는 읽기 모델 자체가 없어 회피 가능하나, 추가 시 application에.
- **`external_id`를 NOT NULL:** 초대 시점 null이어야 함(A-6, D-06). nullable 필수.
- **`externalId`로 업무 관계 참조:** A-6 위반. 매칭은 `User.id`로만.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| 도메인 이벤트 발행·정리 | 수동 이벤트 큐 + publisher 호출 | `AbstractAggregateRoot.registerEvent` + `save()` | save 정합성·자동 clear·무인프라 (D-03) |
| email 유일성 동시성 보장 | 앱 레벨 락/synchronized | DB UNIQUE 제약 (최종 방어) + 선검사(UX) | 선검사만으론 race window 존재 (D-06) |
| VO 동등성 | 수동 equals/hashCode 산발 | record VO 또는 Lombok `@Value`/명시 equals | 일관성·불변 보장 (단 JPA `@Embeddable` 제약 §Pitfall 참조) |
| 영속화 boilerplate | 수동 EntityManager 래핑 | `JpaRepository` 상속 어댑터 (Phase 1 형판) | 파생 쿼리·save 자동 |

**Key insight:** 이 도메인에서 hand-roll 유혹은 거의 없다. 모든 빌딩 블록이 Spring Data/Hibernate 표준에 매핑된다. 유일한 함정은 **VO를 표준에 맞추는 방법**(아래 Pitfalls)이지 새 코드를 쓰는 것이 아니다.

## Common Pitfalls

### Pitfall 1: nullable `@Embeddable`의 all-null=null 함정 (★ 가장 중요)
**무엇이 잘못되나:** Hibernate 6은 `@Embedded` 필드의 **모든 컬럼이 NULL이면 임베디드 객체 자체를 `null`로 인스턴스화**한다(필드만 null인 객체가 아님). `ExternalId`를 `@Embeddable`로 두고 초대 시점(연결 전)엔 null이어야 하는데, 단일 컬럼 VO라 컬럼이 NULL이면 `user.getExternalId()`가 `null`을 반환한다.
**왜 발생:** Hibernate가 null과 "빈 임베디드 값"을 동등 취급. `[CITED: docs.hibernate.org/orm/6.5 userguide; coderanch/hibernate-dev 메일링 확인]`
**회피 (권장 순):**
1. **`ExternalId`를 `String` 컬럼으로 직접 매핑**(위 코드 예시처럼). 단일 값 식별자는 VO 래핑 이득이 작고, nullable 의미가 명확해진다. D-02는 `ExternalId`를 `@Embeddable`로 **권장**하나 `[가정]` 태그가 붙은 평탄화 세부는 재량 — **이 함정 때문에 String 직접 매핑을 1순위 권장.**
2. `@Embeddable` 유지 시: getter가 `null` 반환 가능함을 도메인 코드가 전제하고, `linkIdentity` 가드를 `status == INVITED` 기준으로(externalId null 기준 아님) 작성. equals/hashCode가 null 인스턴스를 다루지 않게.
**경고 신호:** `getExternalId().getValue()` NPE, 연결 전 사용자 조회 시 VO가 null.
**Confidence:** HIGH (공식 문서 + 다수 커뮤니티 사례).

### Pitfall 2: `AbstractAggregateRoot.domainEvents` 필드의 JPA 매핑 충돌
**무엇이 잘못되나:** `AbstractAggregateRoot`는 내부에 `domainEvents` 컬렉션 필드를 가진다. `User`가 `@Entity`로 이를 상속하면 Hibernate가 그 필드를 영속 매핑하려다 실패하거나 의도치 않은 컬럼/테이블을 만들 수 있다.
**왜 발생:** JPA는 상속된 non-transient 필드도 매핑 시도.
**회피:** `AbstractAggregateRoot`의 `@DomainEvents`/`@AfterDomainEventPublication` 메서드와 내부 필드는 Spring Data 설계상 영속 대상이 아니어야 한다. Spring Data가 제공하는 `AbstractAggregateRoot`는 이 필드를 매핑 제외하도록 설계되어 있으나(`@Transient`), **버전·JPA provider 조합에 따라 검증 필요.** planner는 통합 테스트에서 `User` 스키마에 의도한 컬럼만 생성되는지(Flyway 테이블과 Hibernate 매핑 정합) 확인. 불확실하면 명시적 `@DomainEvents` 메서드 + `@Transient List<Object> events` 방식으로 대체(D-03 Discretion 허용).
**Confidence:** MEDIUM (`[ASSUMED]` — `AbstractAggregateRoot`의 `@Transient` 보장을 spring-data-commons 3.5.1 소스로 직접 확인하지 못함. 통합 테스트로 검증 권장).

### Pitfall 3: 이벤트는 커밋 전 발행 — "커밋 후" 가정 금지
**무엇이 잘못되나:** `save()` 시 동기·트랜잭션 내 발행이라, 발행 시점엔 아직 커밋 전이다. Phase 4 구독자가 "비활성화가 DB에 확정된 후" 권한 정리를 가정하면 롤백 시 정합성 깨짐.
**회피:** 이번 Phase는 발행만이라 무영향. 단 RESEARCH·CONTEXT에 명시해 Phase 4가 `@TransactionalEventListener(AFTER_COMMIT)` 채택하도록 계약 고정. `[CITED: Spring Data JPA core-domain-events; @EventListener는 publisher 트랜잭션 컨텍스트 내 동기 실행]`
**Confidence:** HIGH.

### Pitfall 4: 전방 참조 테스트 컴파일 실패 (Phase 1 lesson 재발 위험)
**무엇이 잘못되나:** `User`·`Email` 등 타입 생성 전에 통합/단위 테스트를 먼저 커밋하면 컴파일 RED.
**회피:** Phase 1 lesson대로 — wave 순서로 타입 우선 생성, 또는 미존재 타입 참조 본문을 `@Disabled` + **주석 처리**(`@Disabled` 단독은 불충분, 컴파일 안 됨). `[VERIFIED: 01-2026-05-30.md lesson]`
**Confidence:** HIGH.

### Pitfall 5: ArchUnit 게이트 자기모순 (Phase 1 01-04 재발 위험)
**무엇이 잘못되나:** `ArchitectureTest.hexagonalLayerDependencies()`가 `..domain..`/`..application..` 두 점 패턴으로 신규 `identity` 패키지에 **자동 적용**된다. application→infrastructure 의존이 생기면 빌드 RED.
**회피:** 모든 포트(`UserRepository`)는 domain, 어댑터는 infrastructure. 도메인 이벤트는 domain/event. 예외 타입은 domain 또는 common(application이 infra를 보지 않게). DTO/읽기포트 필요 시 application(이번 Phase 불필요). planner는 PLAN을 커밋된 게이트와 대조 검증(lesson P1). `[VERIFIED: ArchitectureTest.java + 01 lesson]`
**Confidence:** HIGH.

### Pitfall 6: `@Embeddable` 파라미터 파생 쿼리 매핑
**무엇이 잘못되나:** `existsByEmail(Email)` 같은 파생 쿼리가 `@Embeddable` 내부 필드명과 컬럼명 불일치 시 매핑 실패.
**회피:** `@AttributeOverride`로 컬럼명을 명시하고, 파생 쿼리 메서드명이 임베디드 필드 경로와 정합하는지 통합 테스트로 검증. 불확실하면 `@Query`로 명시.
**Confidence:** MEDIUM.

## Runtime State Inventory

> 이 Phase는 **greenfield(신규 컨텍스트 생성)** — 기존 문자열 rename/migration이 아니다. 단, 기존 DB 스키마 위에 신규 테이블을 추가하므로 마이그레이션 정합성만 확인.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | None — `users` 테이블은 신규 생성(`V2__`), 기존 데이터 변환 없음 | 없음 |
| Live service config | None — 외부 서비스 미연동(Phase 3가 OIDC) | 없음 |
| OS-registered state | None | 없음 |
| Secrets/env vars | None — 신규 secret 없음. DataSource는 Phase 1 설정 재사용 | 없음 |
| Build artifacts | None — 신규 의존성 0, 재빌드만 | 없음 (Gradle 재컴파일) |

**검증:** grep으로 `identity` 패키지 부재 확인(`find src` 결과에 없음). `users` 테이블 부재 확인(V1만 존재). 신규 추가만 발생.

## Validation Architecture

> `.planning/config.json`에 `workflow.nyquist_validation`이 명시적 `false`가 아니므로 포함.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 (Jupiter) + AssertJ + Spring Boot Test, Testcontainers 1.21.3 |
| Config file | `gradle/libs.versions.toml` (의존 버전), `src/test/.../AbstractIntegrationTest.java` (싱글턴 컨테이너 베이스) |
| Quick run command | `./gradlew test --tests "*identity*"` (도메인 단위 테스트만 빠르게) |
| Full suite command | `./gradlew test` (ArchUnit + 통합 포함 전체 GREEN) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| IDEN-01 | `invite(email)` → INVITED 저장 + email UNIQUE | integration | `./gradlew test --tests "*UserLifecycleIT"` | ❌ Wave 0 |
| IDEN-02 | `linkIdentity` → INVITED→ACTIVE | unit + integration | `./gradlew test --tests "*UserTest"` | ❌ Wave 0 |
| IDEN-03 | 동일 신원 두 번 연결 → 예외 | unit | `./gradlew test --tests "*UserTest"` | ❌ Wave 0 |
| IDEN-04 | `disable()` → DISABLED + `UserDisabled` 발행 | integration (이벤트 단언) | `./gradlew test --tests "*UserLifecycleIT"` | ❌ Wave 0 |
| IDEN-05 | email UNIQUE + 매칭=로컬 PK | integration (제약 위반 단언) | `./gradlew test --tests "*UserLifecycleIT"` | ❌ Wave 0 |
| IDEN-06 | IdP 필드 갱신, 관리자 필드 보존 | unit (시그니처/동작) | `./gradlew test --tests "*UserTest"` | ❌ Wave 0 |
| 계층 | identity 패키지 의존 방향 | arch | `./gradlew test --tests "ArchitectureTest"` | ✅ (기존 게이트, 자동 적용) |

**관찰 가능한 행위로 SC#1~5 증명:**
1. **SC#1 (invite):** 통합 테스트 — `invite` 후 `findById` 시 status=INVITED. 같은 email로 두 번 저장 시 `DataIntegrityViolationException`(DB UNIQUE) 또는 선검사 예외.
2. **SC#2 (linkIdentity 전이/멱등성):** 단위 — INVITED `User.linkIdentity` 후 status=ACTIVE; ACTIVE 상태에서 재호출 시 `InvalidStateTransition`.
3. **SC#3 (disable + 이벤트):** 통합 — `@RecordApplicationEvents` 또는 테스트 `@EventListener`/`ApplicationEvents`로 `disable()` + `save()` 후 `UserDisabled(userId)` 발행 단언. status=DISABLED.
4. **SC#4 (필드 소유권):** 단위 — 관리자 입력 필드(예: 내부 메모/역할 placeholder)를 set한 뒤 `linkIdentity` 호출 → IdP 필드(displayName)만 갱신, 관리자 필드 불변 단언. (관리자 필드가 이 Phase에 실재하지 않으면 **시그니처 수준 단언**: `linkIdentity`가 관리자 필드를 인자로 받지 않음을 구조적으로 보장 — planner가 최소 placeholder 필드 도입 여부 판단.)
5. **SC#5 (로컬 PK 매칭):** 통합 — `linkIdentity` 후에도 업무 참조는 `User.id`로 조회 가능(불변). email/externalId는 식별용일 뿐임을 테스트가 문서화.

### 이벤트 발행 단언 방법 (HIGH)
Spring Boot 테스트에서 `@RecordApplicationEvents` + `ApplicationEvents` 주입으로 발행 이벤트 수집. 또는 테스트 설정에 `@Component`/`@TestConfiguration` 이벤트 캡처 빈. `disable()` 직후가 아니라 `repository.save()` 직후에 발행됨에 유의(타이밍 Pitfall 3).

### Sampling Rate
- **Per task commit:** `./gradlew test --tests "*identity*"` (단위 우선, 빠름)
- **Per wave merge:** `./gradlew test` (ArchUnit 포함 전체)
- **Phase gate:** 전체 스위트 GREEN — `/gsd:verify-work` 전. Phase 1 lesson대로 `--rerun-tasks`로 독립 재검증.

### Wave 0 Gaps
- [ ] `UserTest.java` (도메인 단위 — 전이/멱등성/소유권, IDEN-02/03/06) — Testcontainers 불필요, 순수 단위
- [ ] `UserLifecycleIT.java` (통합 — `AbstractIntegrationTest` 상속, IDEN-01/04/05 + 이벤트 발행)
- [ ] 프레임워크 추가: **없음** (JUnit5/AssertJ/Testcontainers 모두 Phase 1에 존재)
- [ ] Flyway `V2__create_identity_users.sql` — 테스트가 의존하는 스키마

## Flyway V2 Migration (권장 형태)
Phase 1 규약(`V{n}__desc.sql`, `BIGSERIAL PRIMARY KEY`, `TIMESTAMP WITH TIME ZONE DEFAULT now()`, 테이블 `COMMENT`) 그대로:
```sql
-- V2__create_identity_users.sql  (정확한 컬럼/네이밍은 planner 재량)
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,           -- IDEN-05 유일
    external_id   VARCHAR(255) UNIQUE,                    -- nullable + UNIQUE (A-6, D-06)
    status        VARCHAR(20)  NOT NULL,                  -- @Enumerated(STRING)
    display_name  VARCHAR(255),                           -- IdP 출처 필드
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);
COMMENT ON TABLE users IS 'Identity 컨텍스트 — 사용자 식별 진실 공급원 (Phase 2)';
```
**주의:** Hibernate 매핑(컬럼명·nullable·unique)과 Flyway DDL이 정확히 정합해야 한다. Spring Boot 기본 `ddl-auto=none`(Flyway가 권위) 가정 — Phase 1 application.yml 확인 필요. `users`는 PostgreSQL 예약어가 아니므로 따옴표 불필요(소문자 unquoted OK). `[ASSUMED — ddl-auto 설정 planner가 application.yml로 확인]`

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `@ApplicationModuleListener`/Modulith 이벤트 | Spring Data `AbstractAggregateRoot`(무인프라) | — | Phase 2엔 Modulith 불필요(D-03) |
| 수동 `ApplicationEventPublisher` 주입 | `registerEvent` + save 발행 | Spring Data 1.13+ | 도메인 비오염 |
| `@Embeddable` 클래스 + 수동 equals | Java record as `@Embeddable` (Hibernate 6 `EmbeddableInstantiator`) | Hibernate 6.0 | VO를 record로 — 불변·equals 자동. 단 nullable all-null=null 함정 동일 적용 |

**Deprecated/outdated:** 없음 — 이 Phase는 안정적 표준만 사용.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ExternalId`/`Email` 단일 컬럼 평탄화(`@AttributeOverride`) | D-02, Pattern 1 | 낮음 — planner가 매핑 검증 |
| A2 | `disable()` 이미 DISABLED 시 무연산 vs 예외 미확정 | D-04, Pattern 1 | 낮음 — SC가 정상 전이만 요구 |
| A3 | `UserDisabled` 위치=domain/event, 페이로드=userId만 | D-03 | 낮음 — Phase 4 계약, ID-only가 §4.1 정합 |
| A4 | `AbstractAggregateRoot.domainEvents`가 `@Transient`로 자동 매핑 제외 (Pitfall 2) | Common Pitfalls | **중간** — 잘못되면 스키마 충돌. 통합 테스트로 검증 필수 |
| A5 | `ddl-auto=none`, Flyway가 스키마 권위 | Flyway Migration | 중간 — application.yml 확인 필요 |
| A6 | 관리자 입력 필드가 이 Phase에 실재하는지(SC#4 단언 대상) | Validation §SC#4 | 중간 — 미실재 시 시그니처 수준 단언으로 강등. planner가 placeholder 도입 판단 |
| A7 | `existsByEmail(Email)` 파생 쿼리 임베디드 매핑 정합 | Pattern 3, Pitfall 6 | 낮음 — 통합 테스트로 검증 |

## Open Questions

1. **`disable()` 멱등성:** 이미 DISABLED 상태에서 `disable()` 재호출 시 무연산인가 예외인가?
   - 알고 있는 것: SC#3은 정상 전이만 요구. IDEN-03은 linkIdentity 멱등성만 명시.
   - 불확실: disable 재호출 정책.
   - 권장: 무연산(이벤트 미발행) — 비활성화는 멱등적 상태이므로. planner가 D-04 `[가정]` 해소.
2. **SC#4 관리자 입력 필드 실체:** Phase 2에 관리자 전용 필드(내부 역할 등)가 실재하는가? Phase 4(Authorization)가 역할을 다루므로 Phase 2엔 placeholder만 있을 수 있다.
   - 권장: 최소 placeholder 필드 1개(예: `internalNote` 또는 관리자 설정 displayName 구분) 도입해 SC#4를 행위로 단언, 또는 시그니처 수준 단언으로 명시. planner 판단.
3. **MyBatis 읽기 모델:** D-07/CONTEXT가 선택이라 명시. **권장: 만들지 않는다** — SC가 메서드 호출만 검증하고 복잡 조회 요구가 없다. 사용자 목록 화면이 필요한 admin/업무 Phase에서 추가(스코프 최소화 원칙).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | 빌드/런타임 | ✓ (Phase 1) | 21 | — |
| Docker (Testcontainers) | 통합 테스트 | ✓ (Phase 1 사용) | — | `dockerApiVersion` 옵트인 가드(Phase 1 lesson) |
| PostgreSQL 16 (container) | 통합 테스트 | ✓ (AbstractIntegrationTest) | postgres:16 | — |
| Redis 7 (container) | (이 Phase 미사용, 컨텍스트 캐시 공유) | ✓ | redis:7 | — |

**신규 외부 의존: 없음.** Phase 1 환경 그대로 재사용.

## Sources

### Primary (HIGH confidence)
- `spring-backend-ddd-baseline.md` §4.1~4.6, §5.1, §5.2, §6 — 설계 정본
- `./gradlew dependencies --configuration runtimeClasspath` — Spring Data JPA 3.5.1 / commons 3.5.1 / Hibernate 6.6.18.Final / spring-context 6.2.8 실측
- Spring Data JPA 공식: Publishing Events from Aggregate Roots (core-domain-events) — save/saveAll/delete가 발행 트리거, deleteById 제외, @AfterDomainEventPublication clear, 동기·트랜잭션 내 발행 — https://docs.spring.io/spring-data/jpa/reference/repositories/core-domain-events.html
- Spring Data Core API: AbstractAggregateRoot.registerEvent/domainEvents — https://docs.spring.io/spring-data/commons/docs/current/api/org/springframework/data/domain/AbstractAggregateRoot.html
- Hibernate ORM 6.5 User Guide — @Embeddable, EmbeddableInstantiator(record), all-null embeddable=null — https://docs.hibernate.org/orm/6.5/userguide/html_single/
- `ArchitectureTest.java`, `SampleEntity.java`, `SampleJpaRepository.java`, `AbstractIntegrationTest.java`, `V1__init_schema.sql` — Phase 1 형판 직접 읽음
- `.planning/lessons/01-2026-05-30.md` — 전방 참조 컴파일·ArchUnit 자기모순 lesson

### Secondary (MEDIUM confidence)
- coderanch / hibernate-dev 메일링: all-null @Embedded → null 인스턴스화 동작 사례 — https://coderanch.com/t/629485/databases/columns-Embedded-field-NULL-JPA
- Thorben Janssen: Spring Data JPA Domain Events, Java Records as Embeddables — https://thorben-janssen.com/spring-data-jpa-domain-event/

### Tertiary (LOW confidence — 검증 필요로 표시)
- `AbstractAggregateRoot.domainEvents`의 `@Transient` 자동 매핑 제외(Pitfall 2/A4) — 통합 테스트로 검증 권장

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — 신규 의존 0, 버전 빌드 실측
- Architecture: HIGH — 정본 §5.2 worked example + Phase 1 형판이 거의 완결
- 도메인 이벤트 타이밍: HIGH — 공식 문서 확인
- VO 매핑 함정: HIGH(동작) / MEDIUM(AbstractAggregateRoot @Transient 세부)
- Pitfalls: HIGH (Phase 1 lesson 기반)

**Research date:** 2026-05-31
**Valid until:** 2026-06-30 (안정 스택 — 30일)
