# 03-01 SUMMARY — BFF 인증 Wave 1 (의존성 + findByEmail 포트)

Phase 03 (BFF 인증)의 Wave 1/3. 인증 구현(Wave 2)·테스트(Wave 3)는 이 범위 밖이며 auth 코드/테스트는 생성하지 않았다.

## 변경된 파일

| 파일 | 변경 내용 |
|------|-----------|
| `build.gradle.kts` | oauth2-client, spring-session-data-redis(implementation), spring-security-test, wiremock-standalone(test) 추가 |
| `gradle/libs.versions.toml` | `wiremock = "3.13.1"` 버전 + `wiremock-standalone` 라이브러리 정의 |
| `src/main/java/com/anchors/baseline/identity/domain/repository/UserRepository.java` | `Optional<User> findByEmail(Email email)` 포트 추가 |
| `src/test/java/com/anchors/baseline/identity/UserLifecycleIT.java` | `findByEmailReturnsInvitedUser` 테스트 1건 추가 |

`UserJpaRepository.java`는 변경 없음 — 파생 쿼리가 자동 구현하며 @Query fallback 불필요.

## 추가된 의존성 (BOM 관리, 버전 명시 안 함)

- `org.springframework.boot:spring-boot-starter-oauth2-client` → security 6.5.1 + oauth2-client 6.5.1 전이(NFR-01, security starter 명시 추가 안 함)
- `org.springframework.session:spring-session-data-redis` → 3.5.1 (group은 boot 아님)
- `org.springframework.security:spring-security-test` (test) → 6.5.1
- `org.wiremock:wiremock-standalone:3.13.1` (test, BOM 밖)

기존 의존성·`dockerApiVersion` 가드는 손대지 않음.

## WireMock 버전 — 실제 Maven Central 해석 결과

Maven Central solrsearch 쿼리 결과 최신 stable = **3.13.1** (4.0.0-beta.x는 beta로 제외). jar HEAD 요청으로 200 확인 후 핀.

```
org.wiremock wiremock-standalone 4.0.0-beta.10   (beta — 제외)
org.wiremock wiremock-standalone 3.13.1          (← 선택, latest stable)
org.wiremock wiremock-standalone 3.13.0
```

`testRuntimeClasspath` 해석에서 `org.wiremock:wiremock-standalone:3.13.1` 확정 확인.

## @Query fallback 필요 여부

**불필요.** 파생 쿼리 `findByEmail(Email)`이 boot 시 PropertyReferenceException 없이 정상 동작. existsByEmail과 동일하게 @Embeddable `value` 필드를 자동 해석. UserJpaRepository 무수정.

## 검증 출력 (PASS 증거)

1. **의존성 해석** — 4개 모두 resolve:
   ```
   org.springframework.security:spring-security-oauth2-client:6.5.1
   org.springframework.session:spring-session-data-redis -> 3.5.1
   org.springframework.security:spring-security-test -> 6.5.1
   org.wiremock:wiremock-standalone:3.13.1
   ```
2. **compileJava + compileTestJava** — `BUILD SUCCESSFUL in 3s`
3. **UserLifecycleIT** — tests=6 skipped=0 failures=0 errors=0 (기존 5 + 신규 1, GREEN)
4. **ArchitectureTest** — tests=1 skipped=0 failures=0 errors=0 (레이어 위반 0, GREEN)
5. boot 로그에 PropertyReferenceException 없음 — `BUILD SUCCESSFUL in 6s`

## Acceptance — 전부 충족

- [x] oauth2-client / spring-session-data-redis / spring-security-test / wiremock 모두 resolve
- [x] compileJava + compileTestJava BUILD SUCCESSFUL
- [x] WireMock 3.13.1 — 실제 Maven Central 해석으로 핀
- [x] findByEmail 포트(domain, infra import 없음) 추가
- [x] findByEmailReturnsInvitedUser GREEN (invite된 사용자 id 일치, 없는 email 빈 Optional)
- [x] ArchitectureTest GREEN (0 위반)
- [x] PropertyReferenceException 없음 / @Query fallback 불요

## 편차

없음. IdentityApplicationService 무수정, auth 코드/테스트 미생성, git commit/push 안 함, GSD/Superpowers 내부 파일 미수정.
