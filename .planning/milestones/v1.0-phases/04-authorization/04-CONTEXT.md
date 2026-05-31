# Phase 4: Authorization 컨텍스트 - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

> **수집 방식 주석:** 이 CONTEXT.md는 비대화형(autonomous) 세션에서 작성되었다.
> discuss-phase의 대화형 질문 대신, 각 그레이 영역의 결정을 ROADMAP Success
> Criteria(SC#1~5) · REQUIREMENTS(AUTHZ-01~09) · 정본 표준(`spring-backend-ddd-baseline.md`
> §3 A-4/A-5/A-7 · §4.5 · §4.6 · §5.4 · §5.5 · §6 NFR) · Phase 1/2/3 코드·ArchUnit
> 게이트·lesson 교훈(01~03)에 근거해 직접 내렸다. 사용자 확인이 필요한 가정은 각 결정의
> **[가정]** 태그로 명시했으니 planner가 표면화·확정할 것. 이 Phase는 milestone v1.0의
> 마지막 단계이며, 정본이 "가장 풍부한 도메인"(§5.4)이라 부른 영역이다.

<domain>
## Phase Boundary

전역 역할 / 메뉴 권한 / 리소스 권한의 **3계층 권한 모델**과 **그룹**, **리소스 계층 상속**을
포트/어댑터(A-5) 구조로 구현해, `PermissionEvaluator`가 **직접 부여 ∪ 그룹 ∪ 상위 리소스
상속**을 합산한 **통합 판정**을 내리게 한다. 정본 §5.4가 이 Phase의 worked example을 거의 그대로
기술하며("가장 풍부한 도메인"), 이 절을 1차 설계 기준으로 삼는다.

새 바운디드 컨텍스트 `com.anchors.baseline.authorization.{domain,application,infrastructure,interfaces}`를
신설한다. Phase 2(`identity`)·Phase 3(`auth`)와 동일한 4계층 규약·포트/어댑터 패턴·ArchUnit
게이트를 형판으로 따른다.

범위 (ROADMAP SC#1~5 / AUTHZ-01~09):

- **3계층 권한 부여/회수 (AUTHZ-01·02·03, SC#1):** userId에 **전역 역할(GlobalRole)** 부여/회수,
  사용자·그룹에 **메뉴 권한(MenuGrant)**·**리소스 권한(ResourceGrant)** 부여/회수. 작은 애그리거트로
  쪼갠다(§5.4: 전체 권한 그래프를 하나의 거대 애그리거트로 만들지 않는다).
- **그룹 (AUTHZ-04):** `Group` 생성, 사용자 그룹 가입/탈퇴(`GroupMember`, 다대다, ID 참조). 앱 자체
  관리(§5.5 — 외부 IdP 그룹 동기화 아님).
- **리소스 계층 상속 (AUTHZ-05, SC#3):** `ResourceHierarchy` 정의 → 상위 리소스 권한이 하위로
  상속 → ListObjects 결과에 포함.
- **통합 판정 (AUTHZ-06, SC#2):** `PermissionEvaluator.evaluate(userId, resource, action)` =
  (직접 부여) ∪ (소속 그룹의 부여) ∪ (상위 리소스 상속). 역할 함의(예: EDITOR ⊇ VIEW) 규칙을
  도메인 서비스에 캡슐화.
- **ListObjects 읽기 모델 (AUTHZ-07, SC#3):** "현재 사용자가 접근 가능한 리소스 목록"을 MyBatis
  읽기 모델(재귀 CTE)로 조회.
- **포트/어댑터 (AUTHZ-08, SC#5):** `AuthorizationPort` 인터페이스 + Postgres 내부 어댑터.
  외부 엔진(OpenFGA 등)으로 교체 가능한 구조.
- **미로그인 권한 (AUTHZ-09, SC#4):** INVITED 사용자에게 부여한 권한이 로컬 PK(userId) 기준으로
  저장되고, ACTIVE 전이 후 `evaluate()`에 자동 반영(§5.4: 권한은 ID 기준 저장 → 로그인 시 자동 효력).
- **즉시 반영 (SC#1):** 역할 부여/회수가 `evaluate()` 결과에 즉시 반영(캐시 무효화 또는 매 호출 DB 조회).

**범위 밖 (다른 Phase / Out of Scope):**

- **외부 인가 엔진(OpenFGA 등) 어댑터 구현** — A-7 트립와이어 전까지 보류. 포트는 열어두되 어댑터 2는
  미구현(PROJECT.md Out of Scope, REQUIREMENTS Future AUTHZ-X1/X2). 부패 방지 계층(ACL)·dual-write
  정합성 정책도 외부 엔진 도입 시점의 별도 작업.
- **인증·세션·로그인** — Phase 3 완료. Authorization은 Phase 3 principal이 싣는 **로컬 `User.id`만**
  소비한다(A-4 인증/인가 분리).
- **REST 관리자 API/UI로 권한을 부여하는 화면** — 베이스라인 범위 밖(프론트/UI). 단, `evaluate`를
  Spring Security와 통합하는 진입점(D-05)은 베이스라인 골격의 일부로 포함될 수 있다 — planner가 SC 대비 판단.
- **`UserDisabled` 이벤트 구독으로 권한 일괄 정리** — 정본 §4.6/§5.2가 예시로 들지만 ROADMAP SC가
  명시하지 않음. [가정] 베이스라인 최소 구현은 구독 미포함(Deferred). planner가 SC#1~5와 충돌
  없는지 확인 후 결정(스코프 주의).

</domain>

<decisions>
## Implementation Decisions

### 권한 코드의 패키지 위치 (ArchUnit 게이트 정합성 — lesson 01 P1)
- **D-01:** 권한 코드는 **새 `authorization` 바운디드 컨텍스트**
  (`com.anchors.baseline.authorization.{domain,application,infrastructure,interfaces}`)에 둔다.
  Phase 2 `identity`·Phase 3 `auth`와 동형. 계층 배치 규칙(ArchUnit 게이트는 신규 컨텍스트에 자동 적용):
  - **애그리거트(`GlobalRole`/`MenuGrant`/`ResourceGrant`/`Group`/`GroupMember`/`ResourceHierarchy`),
    VO, 쓰기 리포지토리 포트, `PermissionEvaluator`(순수 도메인 서비스로 둘 경우), `AuthorizationPort`** →
    `authorization/domain` 또는 `authorization/application`.
  - **MyBatis 읽기 포트(ListObjects/evaluate 합산 쿼리 포트)** → **`authorization/application`** (포트),
    **`@Mapper` 구현** → `authorization/infrastructure/mybatis`. **결정적 제약(검증된 형판):** Phase 1
    `SampleQuery`(application 포트) ← `SampleMapper`(infrastructure `@Mapper` implements)가 정확히 이
    패턴이다. 읽기 포트를 domain이 아니라 **application**에 두는 이유가 ArchUnit 게이트 때문임이 01-CONTEXT
    D-05·02-CONTEXT code_context에 고정돼 있다.
  - **JPA 쓰기 어댑터** → `authorization/infrastructure/jpa`.
  - **`AuthorizationPort` Postgres 어댑터** → `authorization/infrastructure`.
- **D-02 (게이트 위반 사전 차단 — lesson 01 P1 직결):** `Application`은 `Domain`만 접근 가능
  (`mayOnlyAccessLayers("Domain")`), `Application→Infrastructure` 금지. 따라서:
  - `PermissionEvaluator`/`AuthorizationPort`가 **JPA/MyBatis 어댑터를 직접 참조하면 빌드 RED.**
    반드시 application/domain의 **포트**만 의존하고, 구현은 infrastructure에 둔다.
  - `evaluate`의 합산 판정이 **재귀 CTE(복잡 조회)** 를 필요로 하므로, evaluate의 데이터 수집은
    **MyBatis 읽기 포트(application) ← `@Mapper`(infrastructure)** 경로를 탄다. `PermissionEvaluator`
    도메인 서비스가 이 읽기 포트를 주입받아 합산·역할 함의 규칙을 적용한다.
  - **[가정 — planner 필수 실증]:** Phase 3가 확립한 "동일 레이어 application→application 호출은 게이트
    허용"(03-CONTEXT D-01, lesson 03 patterns)을 활용할 수 있으나, `authorization` 컨텍스트가 다른
    컨텍스트 application을 의존할 필요는 원칙적으로 없다(userId만 소비). planner는 신규 패키지 추가 후
    `./gradlew test --tests *ArchitectureTest*`로 **계층 정합을 실증**할 것(lesson 01 P1: PLAN↔커밋된 게이트 자기모순 금지).

### `PermissionEvaluator` ↔ Spring Security 통합 (그레이 영역 — 명시 결정 필요)
- **D-03:** `PermissionEvaluator`는 **순수 도메인 서비스**(`authorization/domain` 또는
  `authorization/application`)로 두고, `evaluate(userId, resource, action)` 시그니처로 정의한다(정본
  §5.4 그대로). Spring Security의 `org.springframework.security.access.PermissionEvaluator`
  인터페이스(`evaluate(Authentication, targetDomainObject, permission)`)는 **직접 구현하지 않는다** —
  도메인 서비스를 그 인터페이스에 묶으면 도메인이 Spring Security 타입에 오염되고(A-4 분리 위반), ArchUnit상
  domain이 framework를 의존하게 된다.
  - **근거:** 정본 §5.4가 못박은 시그니처는 `evaluate(userId, resource, action)`이지 Spring의
    `(Authentication, target, permission)`이 아니다. ROADMAP SC#1·#2가 검증하는 대상도 도메인
    `evaluate()`다. Spring `PermissionEvaluator` 인터페이스 시그니처에 도메인을 맞추면 SC와 정본을 모두 비튼다.
  - **[가정 — Spring Security 어댑터, planner 확정]:** Spring Security MethodSecurity(`@PreAuthorize`)와
    연동이 필요하면, **`authorization/infrastructure`에 얇은 Spring `PermissionEvaluator` 어댑터**를 두어
    `Authentication`에서 로컬 `User.id`를 추출(Phase 3 `BaselineOidcUser` principal)하고 도메인
    `PermissionEvaluator.evaluate(userId, resource, action)`에 위임한다(infrastructure→application/domain
    허용, 게이트 정합). `@EnableMethodSecurity` + `PermissionEvaluator` 빈 등록은 **인프라 와이어링**이다.
    단 **이 어댑터를 베이스라인 v1.0에 포함할지는 SC가 요구하지 않으므로** planner가 판단:
    SC#1·#2는 도메인 `evaluate()` 직접 호출로 충분히 검증 가능하다. Spring Security 통합은 골격 완성도를
    위한 선택 항목으로, 포함 시에도 도메인을 오염시키지 않는 어댑터 경계를 지킨다.
  - **클래스명 주의:** 도메인 서비스 이름이 `PermissionEvaluator`라 Spring 동명 인터페이스와 혼동 위험.
    [가정] 도메인 서비스는 정본 명칭 `PermissionEvaluator`를 유지하되, Spring 어댑터를 둘 경우
    `SpringSecurityPermissionEvaluatorAdapter` 류로 명확히 구분(planner 재량).

### `AuthorizationPort` 경계 — 무엇이 포트 뒤에 있는가 (A-5, SC#5, AUTHZ-08)
- **D-04:** `AuthorizationPort`는 **인가 판정·조회·부여의 추상 경계**로, 외부 엔진(OpenFGA)으로
  교체 가능한 연산만 노출한다. [가정 — planner 확정] 최소 연산 집합:
  - `evaluate(userId, resource, action) → boolean` (또는 결정 사유 포함 결과) — 통합 판정
  - `listObjects(userId, action) → List<리소스ID>` (또는 리소스 프로젝션) — 접근 가능 리소스 목록
  - **부여/회수(`grantRole`/`revokeRole`/`grantMenu`/`grantResource`...)를 포트에 넣을지**는 결정 갈림길:
    - **권장:** 부여/회수는 **앱 내부 도메인 애그리거트 쓰기**(JPA)로 처리하고, `AuthorizationPort`에는
      **판정·조회(evaluate/listObjects)만** 둔다. 근거 — 정본 §5.4의 포트/어댑터 교체 동기는 "판정 엔진"
      교체(내부 CTE ↔ OpenFGA relationship tuple)이지, 도메인 애그리거트 자체가 아니다. 부여/회수는 권한
      모델 애그리거트의 책임으로 남기고, 외부 엔진 채택 시 dual-write/ACL은 그 시점의 별도 작업(Out of Scope).
    - **대안:** OpenFGA가 쓰기까지 흡수하므로 `write(tuple)`도 포트에 두는 설계. 이는 외부 엔진 우선
      설계이며 베이스라인의 "내부 어댑터 우선"(PROJECT Key Decision) 원칙과 맞지 않아 **비채택**.
  - planner는 SC#5("AuthorizationPort 인터페이스 + Postgres 내부 어댑터로 교체 가능")를 충족하는
    최소 인터페이스로 확정하되, evaluate/listObjects가 핵심임을 유지한다.

### evaluate() 합산 — 직접 ∪ 그룹 ∪ 리소스 계층 상속 (SC#2·#3, AUTHZ-05·06)
- **D-05:** **합산·상속 조회는 MyBatis 읽기 모델(재귀 CTE), 판정 규칙(역할 함의·합산)은 도메인 서비스.**
  정본 §5.4·§4.5가 명시: 계층 상속(재귀 CTE)·ListObjects는 읽기 측(MyBatis). JPA로 재귀 트리를
  로드하지 않는다(애그리거트 복원 비용·N+1). 구체:
  - 리소스 계층(`ResourceHierarchy`)의 상위→하위 전개는 **Postgres 재귀 CTE**(`WITH RECURSIVE`)로
    MyBatis 매퍼에서 처리(정본 §2 "재귀 CTE 등 활용", PROJECT.md "Postgres + 재귀 CTE").
  - `evaluate`는 (1) userId 직접 부여, (2) userId의 소속 그룹(`GroupMember`) 부여, (3) 대상 리소스의
    조상 리소스에 걸린 부여를 **합집합**으로 모은 뒤, **역할 함의(EDITOR ⊇ VIEW 등) 규칙을 도메인 서비스에서
    적용**해 최종 boolean을 낸다.
  - **[가정 — 역할 함의 모델, planner 확정]:** 정본 부록 갈림길("역할이 액션 묶음 함의 EDITOR=VIEW+EDIT ↔
    액션 직접 부여")에서 베이스라인 기본은 **역할→액션 함의를 도메인 서비스에 캡슐화**(정본 §5.4가 EDITOR ⊇ VIEW를
    예로 듦). 함의 매핑의 구체 표현(enum/테이블)은 planner 재량.
- **D-06 (즉시 반영 — SC#1):** 역할 부여/회수가 `evaluate()`에 **즉시 반영**되어야 한다(SC#1). 베이스라인
  기본은 **매 evaluate 호출 시 DB 조회(캐시 없음)** 로 즉시성을 무비용 보장한다. 캐시(Redis/로컬)는
  도입하지 않는다 — 도입 시 무효화 복잡도가 SC#1을 위협하고(lesson 03: "framework default" 함정과 동류의
  미검증 최적화), NFR-01(외부/커스텀 의존 최소화)에 반한다. 성능 트립와이어(A-7)에 닿으면 그때 캐시 재검토(Deferred).

### 미로그인(INVITED) 권한 저장·표면화 (SC#4, AUTHZ-09) — 크로스 컨텍스트 참조
- **D-07:** Authorization은 Identity `User`를 **로컬 PK(`userId: Long`)로만 참조**한다(§4.1·§5.2,
  02-CONTEXT D-06, A-6). 권한 테이블은 `user_id BIGINT`를 갖되 **identity `users` 테이블로의 FK 제약을
  걸지 않는다.**
  - **근거:** 컨텍스트 간 참조는 객체가 아니라 ID로만(§4.1). 물리 FK를 걸면 두 바운디드 컨텍스트가
    스키마 수준에서 결합되어 컨텍스트 독립성·향후 분리(외부 엔진/별 DB)를 해친다. 무결성은 **부드러운 참조
    (soft reference)** — 애플리케이션 레벨에서 유지. INVITED 사용자에게 부여한 권한은 userId 기준으로 그냥
    저장되고(§5.4: "권한은 ID 기준 저장 → 미로그인 부여해도 저장, 로그인 시 자동 효력"), ACTIVE 전이는
    Identity 컨텍스트 내부 상태 변화일 뿐 Authorization 데이터에 영향을 주지 않는다 → SC#4가 **추가 코드 없이**
    충족된다(상태 전이 후 동일 userId로 evaluate하면 그대로 반영).
  - **[가정 — FK 정책, planner 확정]:** 위 "FK 없음(soft ref)"가 베이스라인 기본이다. 동일 단일 DB·단일
    서비스 전제에서 운영 무결성을 위해 FK를 원하면 planner가 정본 §4.1 컨텍스트 독립 원칙과의 트레이드오프를
    명시하고 결정. 베이스라인 권장은 **FK 없음**(컨텍스트 분리 우선, 외부 엔진 교체 대비).
  - **SC#4 검증 함의:** "INVITED에 부여 → DB 저장 → linkIdentity로 ACTIVE 전이 → evaluate 반영"을
    Identity `linkIdentity` + Authorization grant/evaluate를 엮어 행위로 단언한다(D-10).

### 영속성 — Flyway V3, JPA 방식 A (정본 §4.4, lesson 02 P2)
- **D-08:** 권한 스키마는 **Flyway `V3__create_authorization.sql`**로 추가한다(V1 platform, V2 users
  다음). [가정 — 스키마 윤곽, planner가 도메인 모델에서 역산해 확정] 정본 §5.4·§5.5 + AUTHZ-01~05 기준 테이블:
  - `global_roles` (또는 `user_role`): `user_id`, `role` — 전역 역할 부여(AUTHZ-01)
  - `menu_grants`: 주체(user 또는 group), `menu_id`, 권한 — 메뉴 권한(AUTHZ-02)
  - `resource_grants`: 주체(user 또는 group), `resource_id`, `action`/role — 리소스 권한(AUTHZ-03)
  - `groups`, `group_members`(`group_id`, `user_id`) — 그룹(AUTHZ-04, §5.5)
  - `resource_hierarchy`(`resource_id`, `parent_resource_id`) — 계층, 재귀 CTE 대상(AUTHZ-05)
  - 정확한 컬럼·주체 표현(user/group polymorphic vs 분리 테이블)·인덱스는 **도메인 모델에서 출발해**
    planner가 확정(테이블 우선 금지 — PROJECT Constraints). 단 §5.4 "작은 애그리거트로 쪼갠다"를 준수.
  - **테이블 네이밍 규약:** V1/V2 형판 계승 — `BIGSERIAL PRIMARY KEY`, `TIMESTAMP WITH TIME ZONE
    DEFAULT now()`, 테이블 `COMMENT`.
- **D-09 (JPA 방식 A + created_at 이중 소스 — lesson 02 P2):** 쓰기 애그리거트는 **방식 A**(엔티티=애그리거트,
  02-CONTEXT D-01 계승). **[가정 — 주의] `created_at` 이중 소스**(엔티티 `Instant.now()` + DDL
  `DEFAULT now()`)는 lesson 02 P2가 지적한 **베이스라인 전체 미해소 패턴**이다(SampleEntity·User 동일).
  Phase 4 신규 엔티티도 동일 패턴을 따를지, 아니면 단일화(엔티티 소유 vs DB default 택1)할지 planner가
  **명시 결정**할 것 — 베이스라인 일관성 vs 이중 소스 제거의 트레이드오프. 새 컨텍스트라 단일화를 적용하기
  좋은 지점일 수 있음(P2 액션 후보).

### 검증 전략 (SC#1~5 — lesson 02/03: 행위 단언 + 서버측 상태 직접 확인)
- **D-10:** 전 과정을 **Testcontainers(PostgreSQL) + `@SpringBootTest`/통합 테스트**로 검증한다.
  재귀 CTE는 실 Postgres에서만 정확히 검증되므로 Testcontainers 필수(H2 등 대체 금지). Phase 1
  `AbstractIntegrationTest`(싱글턴 Testcontainers PostgreSQL+Redis) 상속.
  - **SC#1(즉시 반영):** 역할 부여 → evaluate=true, 회수 → evaluate=false를 **연속 단언**(캐시 없으니 즉시).
  - **SC#2(합산 판정):** evaluate **진리표(truth table)** 단언 — 직접만/그룹만/상속만/조합/없음 각 케이스가
    정확한 boolean을 내는지(직접 ∪ 그룹 ∪ 상속). 역할 함의(EDITOR→VIEW)도 케이스로.
  - **SC#3(계층 상속 + ListObjects):** 상위 리소스에 부여 → 하위가 ListObjects 결과에 포함됨을 단언(재귀 CTE 경로).
  - **SC#4(INVITED→ACTIVE 표면화):** INVITED userId에 grant → DB 저장 단언 → `linkIdentity`로 ACTIVE
    전이 → 동일 userId evaluate 반영 단언(Identity+Authorization 엮은 행위 단언, D-07).
  - **SC#5(포트 교체 가능):** `AuthorizationPort` 인터페이스 존재 + Postgres 어댑터가 구현함을 구조적으로
    단언(도메인/애플리케이션이 포트만 의존, 어댑터 주입 교체 가능 — ArchUnit + 주입 테스트).
  - **lesson 03 P1 계승:** RESEARCH의 "프레임워크 기본/Postgres 기본 동작" 주장은 **틀리면 깨지는 테스트로
    뒷받침**(재귀 CTE 문법·MyBatis 매핑은 실 컨테이너로 실측, 추정 금지).
  - **lesson 02 P1 계승:** "VALIDATION 주장 ↔ 실제 테스트 존재" 교차 확인 — SC별 단언이 실제 통합테스트로
    구현됐는지 plan-checker가 확인.

### 빌드 의존성 (최소 — NFR-01)
- **D-11:** **신규 외부 의존 추가 없음(전망).** MyBatis(`mybatis-spring-boot-starter`, Phase 1)·JPA·Postgres·
  Testcontainers·Spring Security(Phase 3)가 이미 존재. 재귀 CTE는 Postgres 네이티브 SQL(MyBatis `@Select`
  또는 XML 매퍼)로 충분 — 추가 라이브러리 불필요. planner가 build.gradle 현황 재확인 후 확정. **OpenFGA SDK
  등 외부 인가 엔진 의존 추가 금지(Out of Scope).**

### Claude's Discretion (planner/researcher 재량)
- 애그리거트별 정확한 VO·필드(예: `RoleName`, `Action`, `ResourceId` VO 표현), 역할 함의 매핑의 구체
  자료구조(enum/테이블), MyBatis 매퍼 표현(`@Select` 어노테이션 vs XML — 재귀 CTE는 XML이 가독성 유리할 수
  있음), 주체(user/group) 권한 부여의 polymorphic 테이블 vs 분리 테이블, `evaluate` 결과 타입(boolean vs
  결정 사유 포함), Spring Security MethodSecurity 어댑터 포함 여부(D-03 [가정]), wave 분할(선형 wave 권장 —
  도메인→인프라(JPA+MyBatis)→테스트), 예외 타입 계층 — 모두 정본 §5.4·§4.5·SC·ArchUnit 게이트·NFR 위반
  없는 선에서 재량.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 정본 표준 (단일 진실 공급원)
- `spring-backend-ddd-baseline.md` (문서 v1.0) — 프로젝트 전체 정본. Phase 4 핵심 절:
  - §2 기술 스택 — DB=**PostgreSQL "재귀 CTE 등 활용"**, 영속성 분담 "JPA=쓰기, MyBatis=복잡 조회·목록".
    D-05·D-08·D-11 근거.
  - §3 **A-4 (인증/인가 분리)** — Authorization은 Phase 3 principal의 로컬 userId만 소비. D-03·D-07 근거.
  - §3 **A-5 (인가 엔진 = 포트/어댑터)** — `AuthorizationPort` 내부(Postgres CTE) ↔ 외부(OpenFGA) 교체.
    D-04·SC#5의 1차 근거.
  - §3 **A-7 (확장성 트립와이어)** — 권한 그래프 복잡도가 한계 넘으면 전용 엔진. 외부 엔진/캐시 Deferred 근거.
  - §4.1 바운디드 컨텍스트 — "권한 컨텍스트가 사용자를 참조할 때 `User` 객체가 아니라 `userId`만 안다."
    D-07(FK 없음·soft ref)의 1차 근거.
  - §4.5 **MyBatis = 읽기 모델(CQRS-lite)** — "내가 접근 가능한 X 목록"은 MyBatis 직접 조회·읽기 DTO.
    D-05·AUTHZ-07 근거.
  - §4.6 트랜잭션·정합성 경계 — "사용자 비활성화 → UserDisabled → 권한 컨텍스트 구독 정리" 예시(Deferred 근거),
    외부 엔진 dual-write 정합성(Out of Scope 근거).
  - §5.4 **인가 컨텍스트(가장 풍부한 도메인)** — 3계층·작은 애그리거트(`MenuGrant`/`ResourceGrant`/`Group`/
    `ResourceHierarchy`)·`PermissionEvaluator.evaluate(userId,resource,action)` 도메인 서비스·재귀 CTE
    읽기 모델·포트/어댑터·미로그인 권한 저장. **이 Phase의 1차 설계 기준.** D-01~D-07의 직접 근거.
  - §5.5 **그룹** — `Group`+`GroupMember`(다대다, ID 참조), 앱 자체 관리. AUTHZ-04·D-08 근거.
  - §6 NFR-01(외부 의존 최소화 → D-06·D-11), NFR-03(외부 권한 최소화 → 그룹 앱 자체 관리).
  - 부록 갈림길 — "인가 구현 내부↔외부", "리소스 역할 모델 역할 함의↔액션 직접". D-04·D-05 [가정] 근거.

### 단계 정의 & 요구사항
- `.planning/ROADMAP.md` — Phase 4 정의, Goal, **Success Criteria 1~5**(즉시 반영·합산 판정·계층 상속+
  ListObjects·INVITED→ACTIVE 표면화·포트 교체 가능).
- `.planning/REQUIREMENTS.md` — **AUTHZ-01 ~ AUTHZ-09**, Future AUTHZ-X1/X2(외부 엔진 — Out of Scope), Traceability.
- `.planning/PROJECT.md` — Key Decision("인가 구현 = Postgres 내부 어댑터 우선, 포트로 교체 가능" / "JPA 방식 A 기본"),
  Constraints(스택·아키텍처 A-1~A-6 고정, 테이블 우선 금지), Out of Scope(외부 엔진 어댑터, 외부 IdP 그룹 동기화).

### Phase 1/2/3 코드·계약·게이트 (반드시 정합)
- `src/test/java/com/anchors/baseline/architecture/ArchitectureTest.java` — **계층 의존 게이트(결정적 제약).**
  `..domain../..application../..infrastructure../..interfaces..` 두 점 패턴이 신규 `authorization` 컨텍스트에
  **자동 적용**. `Application`은 `Domain`만(`mayOnlyAccessLayers("Domain")`) — D-01·D-02 정합 검증 대상.
- `src/main/java/com/anchors/baseline/platform/application/SampleQuery.java` +
  `src/main/java/com/anchors/baseline/platform/infrastructure/mybatis/SampleMapper.java` — **MyBatis 읽기
  포트/어댑터 형판(결정적):** 읽기 포트=application, `@Mapper` implements=infrastructure. D-01·D-05의 직접 형판.
  evaluate/ListObjects 재귀 CTE 매퍼가 이 패턴을 그대로 따른다.
- `src/main/java/com/anchors/baseline/identity/domain/model/User.java` — `User.id`(불변 로컬 PK, `Long`),
  `linkIdentity(externalId, idpDisplayName)`(INVITED→ACTIVE), `created_at` 이중 소스 패턴(엔티티 `Instant.now()`
  + DDL default). D-07·D-09의 참조 대상. **Authorization은 이 `User.id`만 참조(FK 없이).**
- `src/main/java/com/anchors/baseline/identity/application/IdentityApplicationService.java` — `linkIdentity`
  진입점. SC#4 행위 단언(INVITED→ACTIVE)에서 호출.
- `src/main/resources/db/migration/V2__create_identity_users.sql` — `users` 테이블·Flyway 네이밍 형판
  (`V{n}__desc.sql`, `BIGSERIAL`, `TIMESTAMP WITH TIME ZONE DEFAULT now()`, `COMMENT`). D-08은 `V3__...`로 추가.
- `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` — 싱글턴 Testcontainers(PostgreSQL+Redis).
  D-10 상속 대상(재귀 CTE는 실 Postgres 필수).
- `src/main/java/com/anchors/baseline/auth/infrastructure/security/SecurityConfig.java` +
  `auth/infrastructure/security/BaselineOidcUser.java` — Phase 3 principal(로컬 `User.id` 캐리어).
  D-03 [가정] Spring Security 어댑터가 `Authentication`→userId 추출 시 참조. `@EnableMethodSecurity` 부재 확인됨.
- `.planning/phases/02-identity-context/02-CONTEXT.md` — D-06(로컬 PK 매칭 원칙 계승 → D-07), JPA 방식 A(→ D-09).
- `.planning/phases/03-bff-auth/03-CONTEXT.md` — D-01(신규 컨텍스트 + ArchUnit 동일 레이어 호출 실증 패턴), 검증 전략.
- `.planning/lessons/01-2026-05-30.md` — **P1: PLAN ↔ 커밋된 ArchUnit 게이트 자기모순 금지**(D-01·D-02 직결).
  읽기 포트=application 배치가 게이트 회피책임. 선형 wave 전방참조 제거.
- `.planning/lessons/02-2026-05-31.md` — **P1: "VALIDATION 주장 ↔ 테스트 존재" 교차 확인**(D-10).
  **P2: `created_at` 이중 소스 베이스라인 전체 미해소**(D-09 [가정] 직결). 선형 wave 패턴.
- `.planning/lessons/03-2026-05-31.md` — **P1: RESEARCH "프레임워크/기본 동작" 주장은 실측·테스트로 검증**
  (D-10 재귀 CTE·MyBatis 매핑 실측 요구). NFR를 서버측 상태 직접 단언으로 검증(D-06·SC#1). 포트 혼잡 →
  Testcontainers/통합테스트 의존.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **MyBatis 읽기 포트/어댑터 형판** — `platform.application.SampleQuery`(포트) ←
  `platform.infrastructure.mybatis.SampleMapper`(`@Mapper extends 포트`, `@Select`). evaluate/ListObjects
  재귀 CTE가 이 패턴을 그대로 복제한다. `@Mapper` 자동 스캔(`@MapperScan` 불필요).
- **`AbstractIntegrationTest`** — 싱글턴 Testcontainers(PostgreSQL+Redis). 재귀 CTE는 실 Postgres 필수라
  D-10 통합 테스트가 그대로 상속.
- **`User.id`(불변 로컬 PK) + `IdentityApplicationService.linkIdentity`** — Authorization이 참조할 유일한
  Identity 계약(userId). SC#4 행위 단언이 `linkIdentity`를 호출.
- **Flyway 네이밍·DDL 형판** — V1/V2(`BIGSERIAL`, `TIMESTAMP WITH TIME ZONE DEFAULT now()`, `COMMENT`).
  D-08 `V3__create_authorization.sql`이 계승.
- **JPA 방식 A 엔티티 스타일** — `@Entity` 도메인 model 직접, `protected` 무인자 생성자, public setter 금지,
  의미 메서드, `@GeneratedValue(IDENTITY)`→`BIGSERIAL`. 권한 애그리거트가 동형.

### Established Patterns (authorization 컨텍스트가 따를 형판)
- **컨텍스트 = `<context>/{domain,application,infrastructure,interfaces}`** (Phase 1~3 형판). `authorization`도 동일.
  정본 §5.4가 "가장 풍부한 도메인"이라 했으므로 domain 계층이 Identity보다 두텁다(여러 애그리거트 + 도메인 서비스).
- **쓰기 포트/어댑터:** 도메인 리포지토리 포트 ← infrastructure JPA 어댑터.
- **읽기 포트/어댑터:** **application** 읽기 포트 ← infrastructure MyBatis `@Mapper`(게이트 회피 — 결정적).
- **애플리케이션 서비스:** `@Service @RequiredArgsConstructor @Transactional`, 포트만 주입, 비즈니스 규칙 없음(조율).
  단 `PermissionEvaluator`는 **판정 규칙(역할 함의·합산)을 담는 도메인 서비스**라 application 조율과 구분.
- **도메인 이벤트:** `AbstractAggregateRoot` + `registerEvent`(Phase 2 D-03). Phase 4는 발행 필요 시 동일 메커니즘
  (단 SC가 이벤트를 요구하지 않음 — 발행/구독 모두 Deferred 기본).

### Integration Points & ⚠️ 게이트 제약 (lesson 01 P1 — 위반 시 빌드 RED)
- **`ArchitectureTest`가 신규 `authorization` 패키지에 자동 적용.** `Application→Domain만`, `Application→
  Infrastructure 금지`. 함의: `PermissionEvaluator`/`AuthorizationPort`는 포트만 의존, JPA/MyBatis 구현은
  infrastructure. 읽기 포트(ListObjects/evaluate 합산 쿼리)는 **application**에 배치(domain 아님). planner가
  신규 패키지 추가 후 `./gradlew test --tests *ArchitectureTest*`로 실증(lesson 01 P1).
- **크로스 컨텍스트:** Authorization은 Identity `User.id`만 참조(FK 없이 soft ref — D-07). 권한 테이블의
  `user_id`는 identity `users`로 물리 FK를 걸지 않는다(§4.1 컨텍스트 독립).
- **Spring Security 통합(선택):** Phase 3 `BaselineOidcUser` principal에서 로컬 userId 추출. `@EnableMethodSecurity`
  현재 부재 — D-03 [가정] 어댑터 포함 시 SecurityConfig에 추가(infrastructure 와이어링).
- **재귀 CTE 실측:** lesson 03 P1 — Postgres `WITH RECURSIVE` 문법·MyBatis 매핑은 Testcontainers로 실측,
  추정 금지. H2 등 대체 DB 금지.
- **전방 참조 컴파일:** 선형 wave(도메인→인프라(JPA+MyBatis)→테스트)로 전방참조 위험 구조적 제거(Phase 2·3 패턴).
- **`created_at` 이중 소스(D-09 [가정]):** 신규 엔티티가 SampleEntity/User와 동일 이중 소스를 따를지 단일화할지
  planner 명시 결정(lesson 02 P2).

</code_context>

<specifics>
## Specific Ideas

- 정본 §5.4가 이 Phase의 worked example을 거의 그대로 기술한다("가장 풍부한 도메인") — 작은 애그리거트
  4종, `PermissionEvaluator.evaluate(userId, resource, action)` 도메인 서비스, 재귀 CTE 읽기 모델,
  포트/어댑터, 미로그인 권한 저장. 설계는 §5.4를 1차 기준으로 삼고 SC#1~5로 검증한다.
- **핵심 긴장점:** "`PermissionEvaluator`"라는 이름이 Spring Security 동명 인터페이스와 충돌. 정본의
  `evaluate(userId, resource, action)` 도메인 시그니처를 사수하고(A-4 분리), Spring 통합은 인프라
  어댑터로만(D-03). 이 구분이 흐려지면 도메인이 framework에 오염되고 SC가 비틀린다 — 이 Phase 설계의 최대 함정.
- **골격 재사용성(PROJECT Core Value):** `authorization`은 후속 프로젝트가 "복제해서 자기 리소스/메뉴 트리만
  채우는" 네 번째이자 마지막 기반 형판. 3계층 권한·그룹·계층 상속·포트/어댑터가 도메인 비종속으로 명료하게 드러나야 한다.
- **milestone v1.0 마지막 단계:** 이 Phase 완료 시 Identity(신원)·Auth(인증)·Authorization(인가) 기반
  컨텍스트 3종이 모두 갖춰진다. PROJECT Core Value("스택·아키텍처·인증/인가를 다시 결정하지 않도록")의 완성.

</specifics>

<deferred>
## Deferred Ideas

- **외부 인가 엔진(OpenFGA 등) 어댑터 + ACL + dual-write** — A-7 트립와이어 전까지 보류. 포트(D-04)는
  열어두되 어댑터 2는 미구현(REQUIREMENTS Future AUTHZ-X1/X2). 권한 그래프 복잡도가 앱 내부(재귀 CTE) 한계를
  넘는 시점의 별도 작업.
- **권한 평가 캐시(Redis/로컬) + 무효화** — D-06은 즉시성(SC#1)을 위해 매 호출 DB 조회. 성능이 실측으로
  문제되는 시점(A-7)에 캐시+무효화 전략 재검토. 미검증 최적화 선반영 금지(lesson 03 교훈).
- **`UserDisabled` 이벤트 구독 → 권한 일괄 정리** — 정본 §4.6/§5.2가 예시로 들지만 ROADMAP SC 미요구.
  Phase 2가 `UserDisabled` 발행 측만 구현(02-CONTEXT Deferred), Phase 4가 구독자 후보. 베이스라인 최소
  구현은 미포함 — 비활성 사용자 권한 정리가 실제 요구가 되는 시점에 이벤트 기반으로 추가.
- **Spring Security MethodSecurity 어댑터(`@PreAuthorize` + 커스텀 `PermissionEvaluator` 빈)** — D-03 [가정].
  SC#1~5는 도메인 `evaluate()` 직접 호출로 검증 가능하므로 베이스라인 필수 아님. 골격 완성도를 위해 포함할 수
  있으나 도메인 오염 없는 인프라 어댑터 경계 필수 — planner가 SC 대비 비용으로 포함 여부 확정.
- **REST 관리자 API(권한 부여/회수 화면 백엔드)** — 베이스라인 범위 밖(프론트/UI). 외부 진입점이 필요한
  후속 admin API Phase 또는 업무 컨텍스트 소관(Phase 2·3 동일 원칙).
- **`UserActivated` 이벤트 기반 권한 활성화** — Phase 2가 미발행(02-CONTEXT Deferred). D-07이 보였듯 ACTIVE
  전이는 Authorization 데이터에 영향을 안 주므로(권한은 INVITED 시점부터 저장) 이벤트가 불필요. 이벤트 기반
  후속 처리가 실제 필요해지면 재검토.
- **`created_at` 이중 소스 단일화(베이스라인 전체)** — lesson 02 P2. 새 컨텍스트(Phase 4)가 단일화 적용
  시작점이 될 수 있으나, SampleEntity·User까지 일괄 정리는 별도 정리 태스크 소관.

</deferred>

---

*Phase: 4-authorization*
*Context gathered: 2026-05-31*
