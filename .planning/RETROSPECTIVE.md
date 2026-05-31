# Retrospective — Spring DDD 백엔드 베이스라인

## Milestone: v1.0 — 베이스라인 골격

**Shipped:** 2026-05-31
**Phases:** 4 | **Plans:** 14

### What Was Built

재사용 가능한 Spring 헥사고날+DDD 백엔드 골격: 플랫폼 골격(가상 스레드·JPA+MyBatis·Flyway·Redis·ArchUnit 게이트) + Identity(User 생명주기) + BFF 인증(OIDC·토큰 Redis 세션 전용) + Authorization(3계층 권한·재귀 CTE 상속·교체 가능 포트). 전체 51 tests GREEN(실 Postgres Testcontainers).

### What Worked

- **types-first 웨이브 분해** — 도메인+계약 → 어댑터/CTE → 테스트 순서. Phase 4 Wave 3에서 Wave-2 소스 수정 0건(계약 고정이 추정 오류 사전 차단).
- **실 Postgres Testcontainers 검증** — 재귀 CTE·사이클 가드를 H2 아닌 실 DB로 행위 단언. 추정 아닌 실측.
- **commit-then-review + 코드 전용 PR 브랜치** — `.planning` 제외 PR로 리뷰어 초점 유지.
- **완료 선언 전 독립 재실행 검증** — 매 단계 `./gradlew test --rerun-tasks` 직접 확인.

### What Was Inefficient

- **Flyway 적용 마이그레이션 편집 → 라이브 부팅 실패.** 리뷰 수정이 적용된 V3를 편집해 영속 dev DB checksum mismatch. Testcontainers(fresh DB)가 완전히 가려, 51 GREEN인데 라이브 부팅만 실패. 사용자 `task run`이 노출. → V3 복원 + V4 분리로 교정.
- **ArchUnit layered check의 사각** — `@Param`(MyBatis)이 application 포트에 누수했으나 third-party 의존 미검사로 게이트 통과, 코드 리뷰에서야 적발.
- **문서 드리프트** — ROADMAP 진행 현황·STATE 집중 phase가 실제 진척과 어긋난 채로 다수 phase 진행.

### Patterns Established

- 적용된 Flyway 마이그레이션은 불변 — 추가는 새 V 번호로(주석도 체크섬 반영).
- VO는 실제 경계(evaluate 등) 사용처가 확정될 때만 도입(투기적 VO 금지).
- 비롤백 @SpringBootTest는 클래스별 AtomicLong 시퀀스 베이스로 격리.

### Key Lessons

- **Testcontainers GREEN ≠ 라이브 부팅 OK.** 영속 DB·마이그레이션 정합은 별도 검증 필요.
- 프레임워크 기본값 주장은 테스트로 뒷받침(Phase 3 HttpSessionOAuth2AuthorizedClientRepository 사례).
- 게이트가 못 잡는 누수(third-party import)는 ArchUnit 규칙 추가로 자동화 후보.

### Cost Observations

- Sessions: 다중 세션(컨텍스트 압축 1회 이상)
- Notable: 4 phase 전부 plan→execute(wave 병렬)→review→retro→secure→ship 파이프라인 완주

## Cross-Milestone Trends

*(v1.0 단일 마일스톤 — 추세는 다음 마일스톤부터 집계)*
