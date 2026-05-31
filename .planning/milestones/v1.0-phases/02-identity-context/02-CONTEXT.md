# Phase 2: Identity 컨텍스트 - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

> **수집 방식 주석:** 이 CONTEXT.md는 비대화형(autonomous) 세션에서 작성되었다.
> discuss-phase의 대화형 질문 대신, 각 그레이 영역의 결정을 ROADMAP Success
> Criteria(SC#1~5) · REQUIREMENTS(IDEN-01~06) · 정본 표준(`spring-backend-ddd-baseline.md`
> §4.4·§4.6·§5.2) · Phase 1 코드/ArchUnit 게이트에 근거해 직접 내렸다. 사용자 확인이
> 필요한 가정은 각 결정의 **[가정]** 태그로 명시했으니 planner가 표면화할 것.

<domain>
## Phase Boundary

사용자 식별의 **진실 공급원(source of truth)**인 Identity 바운디드 컨텍스트를, DDD 애그리거트로 구현한다. 범위는 `User` 애그리거트의 생명주기 3개 연산과 그 불변식이다:

- **초대(invite):** 관리자가 미로그인 사용자를 이메일로 등록 → `INVITED` (IDEN-01)
- **신원 연결(linkIdentity):** 최초 로그인 시 외부 식별자(IdP oid) 연결 → `INVITED→ACTIVE` 전이, 멱등성 가드(같은 신원 두 번 연결 거부) (IDEN-02, IDEN-03)
- **비활성화(disable):** `DISABLED` 전이 + `UserDisabled` 도메인 이벤트 발행 (IDEN-04)
- **필드 소유권:** IdP 출처 필드(이름 등)는 linkIdentity 시 갱신, 관리자 입력 필드는 보존 (IDEN-06)
- **불변 식별자 매칭:** 이메일은 유일, 로그인 후 매칭은 로컬 PK(`User.id`)로만 (IDEN-05, A-6)

새 컨텍스트 디렉터리 `com.anchors.baseline.identity.{domain,application,infrastructure,interfaces}`를 처음으로 생성한다. Phase 1의 `platform`/`common` 골격과 동일한 계층 규약·포트/어댑터 패턴을 형판으로 삼는다.

**범위 밖 (다른 Phase 소관):**
- 실제 OIDC 로그인 흐름·Spring Security·Redis 세션 — **Phase 3 (BFF 인증)**. Phase 2의 `linkIdentity`는 Phase 3가 호출할 **도메인 연산**으로서만 존재한다(5.1: 인증은 횡단 인프라, 도메인과 닿는 지점은 신원 연결뿐).
- `UserDisabled` 이벤트를 **구독·소비**하는 권한 정리 — **Phase 4 (Authorization)**. Phase 2는 이벤트를 **발행**만 한다.
- 권한·역할·그룹 모델 — Phase 4.

</domain>

<decisions>
## Implementation Decisions

### JPA 매핑 방식 (정본 §4.4)
- **D-01:** `User` 애그리거트는 **방식 A(실용형)** 로 매핑한다. JPA 엔티티가 곧 애그리거트 루트다. PROJECT.md Key Decision(방식 A 기본)과 Phase 1 `SampleEntity` 패턴(도메인 model에 `@Entity` 직접, 무인자 생성자 `protected`, public setter 금지)을 그대로 계승한다. 상태 변경은 의미 있는 메서드(`invite`/`linkIdentity`/`disable`)로만, 생성은 정적 팩토리/생성자로만 한다. 방식 B(엄격 분리)는 채택하지 않는다 — Identity 도메인 규칙은 방식 A로 강제 가능한 수준이고, 이중 모델 부담이 골격 재사용성을 해친다.

### 값 객체(VO) 매핑
- **D-02:** `Email`, `ExternalId`는 **`@Embeddable` 값 객체**로, `UserStatus`는 **`@Enumerated(EnumType.STRING)` enum**으로 매핑한다(정본 §4.3·§5.2가 VO를 명시). `Email`은 생성 시점 형식 검증, `ExternalId`는 불변·nullable(초대 시점엔 null, 연결 후 set). 원시 `String`을 흘리지 않는다.
  - **[가정]** 컬럼 평탄화: `Email`·`ExternalId`는 단일 컬럼(`email`, `external_id`)으로 `@Embedded`. 다중 필드 VO가 아니므로 단순 매핑. planner가 `@AttributeOverride` 필요 여부 확정.

### 도메인 이벤트 발행 메커니즘 (정본 §4.6)
- **D-03:** `UserDisabled` 이벤트는 **Spring `ApplicationEventPublisher` 기반**으로 발행한다. 구체적으로 Spring Data의 **`AbstractAggregateRoot` + `@DomainEvents`** 패턴을 채택해, 애그리거트가 이벤트를 누적하고 리포지토리 `save()` 시점에 발행되게 한다(트랜잭션 커밋과 정합). 별도 이벤트 인프라(Spring Modulith, 외부 메시지 브로커)는 도입하지 않는다 — Phase 1 Deferred에서 "도메인 이벤트가 실제 필요해지는 시점에 재검토"로 미뤘고, Phase 2는 **발행 측만** 필요하므로 Spring 기본 메커니즘으로 충분하다.
  - **근거:** Phase 1에 이벤트 인프라가 전혀 없음(grep 확인). 신규 도입 최소화 원칙(NFR-01) + 단일 트랜잭션=단일 애그리거트(정본 §4.6)에 `AbstractAggregateRoot`가 정확히 부합.
  - **[가정]** 이벤트 클래스 위치 = `identity/domain/event/UserDisabled`(정본 §4.2 패키지 규약). 페이로드는 불변 식별자 `userId`(로컬 PK)만 담는다(컨텍스트 간 참조는 ID로만 — §4.1). `UserActivated`(§5.2가 언급)도 발행할지는 ROADMAP SC가 `UserDisabled`만 요구하므로 **SC 미요구 → 발행하지 않음**(스코프 최소화). planner가 Phase 4 구독 필요성과 함께 재확인.

### 불변식 강제 위치 (멱등성·전이·소유권)
- **D-04:** 모든 비즈니스 규칙은 **`User` 애그리거트 메서드 내부**에 둔다(정본 §4.3: 애플리케이션 서비스에 규칙 금지). 구체적으로:
  - **상태 전이 가드:** `linkIdentity`는 `INVITED`에서만 허용, 이미 `ACTIVE`(externalId 존재)면 예외(IDEN-03 멱등성). `disable`은 임의 상태에서 `DISABLED`로(또는 [가정] 이미 DISABLED면 무연산/예외 — planner 확정).
  - **필드 소유권(IDEN-06):** `linkIdentity(externalId, idpName)`이 IdP 출처 필드(이름)만 갱신하고, 관리자 입력 필드는 인자로 받지 않아 **구조적으로** 덮어쓰기 불가하게 설계한다(메서드 시그니처로 소유권 강제).
- **D-05:** 도메인 예외는 **`common` 또는 `identity/domain`의 도메인 예외 타입**으로 던진다(원시 `IllegalStateException` 남발 금지). 정확한 예외 타입/패키지는 planner 재량이나, 멱등성 위반·잘못된 전이를 구분 가능한 타입이어야 한다.

### 유일성 제약 & 매칭 (IDEN-05, A-6)
- **D-06:** **이메일 유일성은 DB UNIQUE 제약(Flyway) + 도메인/애플리케이션 레벨 선검사 병행**으로 보장한다. DB 제약이 최종 방어선(동시성), 애플리케이션 선검사가 사용자 친화적 예외. `external_id`는 **nullable + UNIQUE**(연결 전 null 허용, A-6 불변 매칭).
  - **로그인 후 매칭 = `User.id`(로컬 PK)로만.** 이메일·externalId는 연결/식별용일 뿐 업무 관계 참조 키가 아님(§5.2 핵심 설계 포인트). 이 원칙은 Phase 3·4가 준수하도록 ROADMAP·이 CONTEXT에 고정.

### 영속성 검증 & 인터페이스 노출 범위
- **D-07:** Phase 2 산출물은 **domain + application + infrastructure(JPA 어댑터 + Flyway) + 단위/통합 테스트**까지다. **REST 컨트롤러(interfaces)는 이 Phase에서 만들지 않는다.**
  - **근거:** ROADMAP SC#1~5가 전부 `User.invite()`/`linkIdentity()`/`disable()` **메서드 호출 수준**으로 기술됨(HTTP 엔드포인트 아님). `invite`의 호출자는 관리자 유스케이스, `linkIdentity`의 호출자는 Phase 3 인증 흐름 — 둘 다 아직 외부 진입점이 없다. 컨트롤러를 지금 만들면 인증 없는 노출(보안)·미사용 표면이 된다.
  - **[가정]** 검증은 **Testcontainers 통합 테스트(JPA save/조회 + UNIQUE 제약 + 이벤트 발행) + 도메인 순수 단위 테스트(전이/멱등성/소유권)**로 SC#1~5를 증명한다. Phase 1의 `AbstractIntegrationTest`(싱글턴 Testcontainers) 재사용.

### Claude's Discretion (planner/researcher 재량)
- 도메인 예외 클래스 계층·정확한 패키지(`common` vs `identity/domain`), `Email` 검증 정규식 수준, `UserStatus` enum 값 표현, `@Embeddable` 컬럼 평탄화 세부(`@AttributeOverride`), `AbstractAggregateRoot` vs 명시적 `@DomainEvents` 메서드 선택, 애플리케이션 서비스 트랜잭션 경계 표현, Flyway 마이그레이션 파일 네이밍(`V2__...`), 테스트 슬라이스 구성 — 모두 정본 표준·SC·ArchUnit 게이트 위반 없는 선에서 재량.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 정본 표준 (단일 진실 공급원)
- `spring-backend-ddd-baseline.md` (문서 v1.0) — 프로젝트 전체 정본. Phase 2 핵심 절:
  - §3 A-4 (인증/인가 분리), A-6 (불변 식별자 기반 매칭) — D-06 근거
  - §4.1 바운디드 컨텍스트 (컨텍스트 간 참조는 ID로만, 연동은 도메인 이벤트/포트)
  - §4.2 패키지/계층 구조 — `identity/{domain,application,infrastructure,interfaces}` 신설
  - §4.3 빌딩 블록 (애그리거트 루트·VO·도메인 이벤트·애플리케이션 서비스 규칙 금지) — D-04
  - **§4.4 JPA 방식 A(실용형)** — D-01의 정본 근거 (setter 금지, 정적 팩토리, protected 무인자 생성자)
  - **§4.6 트랜잭션·정합성 경계** (1 트랜잭션 = 1 애그리거트, 컨텍스트 간 변경은 도메인 이벤트 + 최종적 일관성) — D-03 근거
  - **§5.2 식별/온보딩 컨텍스트 (Identity)** — `User` 애그리거트·`Email`/`ExternalId`/`UserStatus` VO·invite/linkIdentity/disable 규칙·필드 소유권·`UserDisabled` 이벤트의 worked example. **이 Phase의 1차 설계 기준.**
  - §6 NFR-01(외부 의존 최소화 → D-03), NFR-06(Flyway → D-06)

### 단계 정의 & 요구사항
- `.planning/ROADMAP.md` — Phase 2 정의, Goal, Success Criteria 1~5 (메서드 호출 수준 — D-07 근거)
- `.planning/REQUIREMENTS.md` — IDEN-01 ~ IDEN-06, Traceability
- `.planning/PROJECT.md` — Key Decisions(JPA 방식 A 기본), Constraints(스택·아키텍처 고정)

### Phase 1 코드 형판 & 게이트 (반드시 정합)
- `.planning/phases/01-platform-skeleton/01-CONTEXT.md` — D-02(루트 패키지 `com.anchors.baseline`), D-03(컨텍스트별 디렉터리 생성), D-05(ArchUnit 게이트)
- `.planning/lessons/01-2026-05-30.md` — **P1 액션: PLAN이 커밋된 ArchUnit 게이트와 자기모순하지 않는지 검증**(01-04 application→infrastructure 위반 재발 방지). 전방 참조 테스트 컴파일 이슈(`@Disabled`+본문 주석) 교훈.
- `src/test/java/com/anchors/baseline/architecture/ArchitectureTest.java` — **계층 의존 게이트(아래 code_context 참조). `..domain..` 패턴이 `identity` 하위까지 자동 적용됨.**

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`AbstractIntegrationTest`** (`src/test/java/com/anchors/baseline/AbstractIntegrationTest.java) — 싱글턴 Testcontainers(PostgreSQL+Redis, Spring 컨텍스트 캐시 공유). Identity 통합 테스트가 그대로 상속.
- **Flyway 마이그레이션 규약** (`src/main/resources/db/migration/V1__init_schema.sql`) — `V{n}__desc.sql`, `BIGSERIAL PRIMARY KEY`, `TIMESTAMP WITH TIME ZONE DEFAULT now()`, 테이블 `COMMENT`. Identity는 `V2__...`로 `identity_user` 류 테이블 추가.

### Established Patterns (Identity가 그대로 따를 형판)
- **쓰기 포트/어댑터:** 도메인에 리포지토리 인터페이스(`platform.domain.repository.SampleRepository`) ← infrastructure의 Spring Data JPA 어댑터(`infrastructure.jpa.SampleJpaRepository extends JpaRepository<E,Id>, <Port>`). Identity: `identity.domain.repository.UserRepository` ← `identity.infrastructure.jpa.UserJpaRepository`.
- **읽기 포트/어댑터:** 읽기 포트는 **application** 계층에 둔다(`platform.application.SampleQuery`) ← MyBatis `@Mapper`가 implements(`infrastructure.mybatis.SampleMapper`). ⚠️ 읽기 포트가 domain이 아니라 **application**에 있는 이유는 ArchUnit 게이트 때문(아래). Phase 2는 복잡 조회 요구가 없어 MyBatis 읽기 모델은 **선택**(SC가 메서드 호출 검증만 요구) — planner 판단.
- **JPA 엔티티 스타일:** `@Entity`/`@Table` 도메인 model에 직접, `@GeneratedValue(IDENTITY)`→`BIGSERIAL`, `protected` 무인자 생성자, public setter 없음, `@Getter`(Lombok), `Instant` 타임스탬프. (`SampleEntity.java`)
- **애플리케이션 서비스:** `@Service @RequiredArgsConstructor @Transactional`, 포트만 주입, 비즈니스 규칙 없음(조율만). (`SampleApplicationService.java`)

### Integration Points & ⚠️ 게이트 제약 (lesson P1 — 위반 시 빌드 RED)
- **`ArchitectureTest.hexagonalLayerDependencies()`가 신규 `identity` 패키지에 자동 적용된다**(`..domain..`/`..application..` 두 점 패턴). 규칙:
  - `Domain` → 어떤 계층도 의존 불가
  - **`Application` → `Domain`만** 의존 가능 (**application이 infrastructure 의존 금지** — Phase 1 01-04에서 DTO를 infra에 두려다 이 게이트와 자기모순한 사례 재발 방지)
  - `Interfaces` → `Application`, `Domain`
  - `Infrastructure` → `Domain`, `Application` (포트 구현)
  - **함의:** 모든 포트 인터페이스는 domain 또는 application에 두고, JPA/MyBatis 구현은 infrastructure에 둔다. DTO/커맨드/쿼리/읽기포트는 application에 배치(infra 금지). 도메인 이벤트 클래스는 domain에.
- **전방 참조 테스트 컴파일:** 타입 생성 전 테스트를 먼저 두면 컴파일 실패. lesson대로 `@Disabled`+본문 주석 또는 wave 순서로 타입 우선 생성.
- Phase 3(BFF 인증)이 `linkIdentity`를, Phase 4(Authorization)가 `UserDisabled` 이벤트와 `User.id`를 소비한다 — Phase 2의 공개 API(애그리거트 메서드 시그니처·이벤트 페이로드)가 이들의 계약이 된다.

</code_context>

<specifics>
## Specific Ideas

- 정본 §5.2가 이 Phase의 worked example을 거의 그대로 기술한다 — `User` 루트, `Email`/`ExternalId`/`UserStatus` VO, invite/linkIdentity/disable, 필드 소유권, `UserDisabled` 이벤트, 로컬 PK 매칭. 설계는 이 절을 1차 기준으로 삼고 SC#1~5로 검증한다.
- 골격 재사용성 원칙(PROJECT Core Value): Identity 산출물은 후속 프로젝트가 "복제해서 업무 도메인만 채우는" 두 번째 형판(첫 형판은 Phase 1 platform). 따라서 컨텍스트 신설 → 애그리거트 → VO → 영속성 → 이벤트의 전체 슬라이스가 명료한 규약으로 드러나야 한다.

</specifics>

<deferred>
## Deferred Ideas

- **`UserActivated` 이벤트** — 정본 §5.2가 `UserActivated`/`UserDisabled` 둘 다 언급하나, ROADMAP SC#3은 `UserDisabled`만 요구. 발행 측만 필요하고 현재 구독자가 없으므로 Phase 2 미발행. Phase 3/4에서 ACTIVE 전이 후속 처리(예: 권한 효력 발생)가 이벤트 기반으로 필요해지면 그때 추가.
- **REST 컨트롤러(interfaces) + 관리자 invite API** — Phase 2는 도메인+애플리케이션까지(D-07). 외부 진입점(인증된 관리자 API, 로그인 콜백)은 Phase 3(BFF 인증) 이후 또는 별도 admin API Phase 소관.
- **MyBatis 읽기 모델(사용자 목록 조회 등)** — Phase 2 SC에 복잡 조회 요구 없음. 사용자 목록·검색 화면이 필요해지는 시점(업무 컨텍스트 또는 admin)에서 추가.
- **Spring Modulith** — Phase 1에서 이미 Deferred. Phase 2는 Spring 기본 이벤트(`AbstractAggregateRoot`)로 충분하므로 여전히 미도입. 컨텍스트 간 이벤트 구독이 다수가 되는 Phase 4에서 재검토.

</deferred>

---

*Phase: 2-identity-context*
*Context gathered: 2026-05-31*
