# 02-02 SUMMARY — Identity 영속화 어댑터 + 애플리케이션 서비스 (Wave 2)

**완료일:** 2026-05-31
**Wave:** 2 / 3 (Wave 1 도메인 완료 전제, Wave 3 테스트는 미포함)

## 생성된 파일

| 파일 | 계층 | 역할 |
|------|------|------|
| `src/main/java/com/anchors/baseline/identity/infrastructure/jpa/UserJpaRepository.java` | infrastructure | `extends JpaRepository<User, Long>, UserRepository` 포트 어댑터. 추가 메서드 없음 |
| `src/main/java/com/anchors/baseline/identity/application/EmailAlreadyExists.java` | application | RuntimeException 서브클래스(메시지 ctor). email 중복 선검사 위반 |
| `src/main/java/com/anchors/baseline/identity/application/IdentityApplicationService.java` | application | `@Service @RequiredArgsConstructor @Transactional`. 포트만 주입, 조율만 |

## TASK 1 — UserJpaRepository 어댑터

- `public interface UserJpaRepository extends JpaRepository<User, Long>, UserRepository {}` (Phase 1 SampleJpaRepository 형판 그대로).
- 포트 시그니처(`save`/`findById`/`existsByEmail(Email)`/`findByExternalId(String)`) 외 public 메서드 없음.
- `existsByEmail(Email email)` 는 파생 쿼리 그대로 유지 — `@Embeddable Email`(필드 `value` → 컬럼 `email`, `@AttributeOverride` 적용됨)을 Spring Data 가 임베디드 경로로 해석. 임베디드 매핑 검증은 Wave 3 통합 테스트 소관(RESEARCH §Pitfall 6).
- MyBatis 읽기 모델 미추가(Open Q3 — 이 Phase 아님). 비즈니스 규칙·`@Transactional` 미추가.

## TASK 2 — IdentityApplicationService + EmailAlreadyExists

- `EmailAlreadyExists` 를 **application 패키지**에 배치(D-06 UX 선검사). infrastructure 가 아님 — ArchUnit application→domain 정합 유지.
- 서비스는 `private final UserRepository userRepository;` **포트만** 주입. UserJpaRepository 어댑터 타입 미주입, ApplicationEventPublisher 미주입.
- 3개 메서드, 모두 조율만 — 비즈니스 가드(`if status !=` 등) 없음. 규칙은 User 애그리거트에:
  - `Long invite(String emailValue)`: `new Email` → `existsByEmail` 선검사(true 면 `EmailAlreadyExists`) → `User.invite(email)` → `save` → `getId()`.
  - `void linkIdentity(Long, String, String)`: `findById().orElseThrow` → `user.linkIdentity(...)` → `save` (전이/멱등성 가드는 User 내부).
  - `void disable(Long)`: `findById().orElseThrow` → `user.disable()` → `save` (save 가 UserDisabled 발행 트리거).
- findById 부재 예외는 최소화 — `IllegalArgumentException`(별도 NotFound 타입 미도입, 과설계 회피).

## 검증 증거

```
$ ./gradlew compileJava 2>&1 | tail -8
> Task :compileJava
BUILD SUCCESSFUL in 653ms
1 actionable task: 1 executed
```

```
$ grep -rn "identity.infrastructure" src/main/java/com/anchors/baseline/identity/application/
NO infrastructure imports in application layer
```

**application 계층 infrastructure import 0건 확인 완료.** 포트(UserRepository, domain) → 어댑터(UserJpaRepository, infrastructure) → 서비스는 포트만 주입 — Phase 1 01-04 자기모순 비재발.

## 편차

없음. 계획대로 3개 파일 생성, compileJava GREEN, ArchUnit 방향성(application→infrastructure 금지) 위반 없음. 테스트는 Wave 3 — 미작성.
