# 02-01 SUMMARY — Identity 도메인 계층 + Flyway V2 (Wave 1)

**완료일:** 2026-05-31
**Plan:** 02-01 (Wave 1/3 — 도메인 계층 + V2 마이그레이션 ONLY, 테스트는 Wave 3)
**검증:** `./gradlew compileJava` → **BUILD SUCCESSFUL**

## 생성 파일 (7건)

| 파일 | 역할 |
|------|------|
| `src/main/java/com/anchors/baseline/identity/domain/model/UserStatus.java` | 상태 enum INVITED/ACTIVE/DISABLED |
| `src/main/java/com/anchors/baseline/identity/domain/model/Email.java` | @Embeddable VO, 생성 시점 형식 검증(공백·@ 부재 거부), equals/hashCode on value |
| `src/main/java/com/anchors/baseline/identity/domain/model/User.java` | 애그리거트 루트 — `extends AbstractAggregateRoot<User>`, invite/linkIdentity/disable/updateInternalNote |
| `src/main/java/com/anchors/baseline/identity/domain/event/UserDisabled.java` | `record UserDisabled(Long userId)` — ID 참조만 |
| `src/main/java/com/anchors/baseline/identity/domain/exception/InvalidStateTransition.java` | RuntimeException 도메인 예외 (메시지 생성자) |
| `src/main/java/com/anchors/baseline/identity/domain/repository/UserRepository.java` | 포트 — save/findById/existsByEmail/findByExternalId (4 메서드, infra import 없음) |
| `src/main/resources/db/migration/V2__create_identity_users.sql` | users 테이블 (7 컬럼) |

## 핵심 설계 결정 (RESEARCH/Plan 준수)

- **Pitfall 1 회피:** `externalId` 는 nullable `String @Column(name="external_id", unique=true)` 직접 매핑 (NOT @Embeddable, NOT NULL 아님). `email` 만 `@Embedded`.
- **멱등성 가드 (IDEN-03):** `linkIdentity` 는 status != INVITED 면 `InvalidStateTransition` — externalId null 여부가 아니라 status 기준.
- **Open Q1 (disable 멱등성):** 이미 DISABLED 면 즉시 return → 무연산·이벤트 미발행.
- **IDEN-06 구조적 강제:** `linkIdentity(String externalId, String idpDisplayName)` 시그니처에 internalNote 인자 없음(grep 확인). 관리자 필드는 `updateInternalNote()` 의미 메서드로만 설정 — public setter 없음.
- **도메인 비오염:** User 에 ApplicationEventPublisher 미주입. `registerEvent(new UserDisabled(this.id))` 만 사용.

## ddl-auto: validate 정합 (V2 ↔ @Entity)

| 컬럼 | DDL | @Entity 매핑 |
|------|-----|-------------|
| id | BIGSERIAL PRIMARY KEY | @Id @GeneratedValue(IDENTITY) Long |
| email | VARCHAR(255) NOT NULL UNIQUE | @AttributeOverride name=email, nullable=false, unique=true |
| external_id | VARCHAR(255) UNIQUE (nullable) | @Column(name=external_id, unique=true) String |
| status | VARCHAR(20) NOT NULL | @Enumerated(STRING) @Column(nullable=false) |
| display_name | VARCHAR(255) (nullable) | @Column(name=display_name) String |
| internal_note | VARCHAR(255) (nullable) | @Column(name=internal_note) String |
| created_at | TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL | @Column(nullable=false, updatable=false) Instant |

+ `COMMENT ON TABLE users` 존재. external_id NOT NULL 부재 확인.

## 검증 증거

- `./gradlew compileJava` → BUILD SUCCESSFUL in 784ms
- grep: `extends AbstractAggregateRoot<User>` ✓ / `registerEvent(new UserDisabled` ✓ / `public void set` → none ✓ / UserRepository infra import → none(주석만) ✓

## 편차

없음. Plan 02-01 의 모든 acceptance criteria 충족. 테스트(UserTest/UserLifecycleIT)는 Wave 3 범위로 미작성.
