# 01-03 실행 요약: 런타임 설정 + 로컬 인프라

**실행일:** 2026-05-30
**플랜:** 01-03 (Wave 2 — application.yml / Flyway 초기 마이그레이션 / Docker Compose)
**상태:** 완료

---

## 생성한 파일

| 파일 | 목적 | 관련 요구사항 |
|------|------|--------------|
| `src/main/resources/application.yml` | 런타임 설정 (가상 스레드·DataSource·JPA·Flyway·Redis·Actuator·로깅) | PLAT-01, PLAT-03, PLAT-05, PLAT-06 |
| `src/main/resources/db/migration/V1__init_schema.sql` | Flyway 초기 마이그레이션 (platform_sample 샘플 테이블) | PLAT-03, PLAT-04(JPA+MyBatis 검증 대상 테이블) |
| `compose.yaml` | 로컬 개발용 PostgreSQL 16 + Redis 7 (D-06) | PLAT-03, PLAT-05 |

---

## application.yml 구성 요약

- `spring.application.name: baseline`
- `spring.threads.virtual.enabled: true` — PLAT-01 (RESEARCH 패턴 1)
- `spring.datasource` — `jdbc:postgresql://localhost:5432/baseline`, user/pass `baseline`, 드라이버 `org.postgresql.Driver`
- `spring.jpa` — `ddl-auto: validate` (Flyway가 스키마 관리), `show-sql: false`, `format_sql: false`, `open-in-view: false` (OSIV off, 계층 경계)
- `spring.flyway` — `locations: classpath:db/migration`, `baseline-on-migrate: false` (greenfield), `validate-on-migrate: true`. flyway-database-postgresql가 클래스패스에 있어 별도 url 불필요 (RESEARCH 패턴 3)
- `spring.data.redis` — `localhost:6379`, 비밀번호 없음, Lettuce 기본 (RESEARCH 패턴 4)
- `management` — 노출 `health,info`; `health.show-details: always` + dev-only 주석(운영은 when_authorized, T-01); `show-components: always`; `db.enabled`/`redis.enabled` true, `diskspace.enabled` false (RESEARCH 패턴 5)
- `logging.level` — `com.anchors.baseline: DEBUG`, `org.flywaydb: INFO`

---

## 검증 결과

| 검증 | 명령 | 기대 | 실제 |
|------|------|------|------|
| 가상 스레드 속성 | `grep -c "virtual:" application.yml` | ≥1 | 1 ✓ |
| Flyway location | `grep "classpath:db/migration"` | 존재 | line 28 ✓ |
| OSIV off | `grep "open-in-view: false"` | 존재 | line 21 ✓ |
| Actuator show-details | `grep "show-details: always"` | 존재 (dev 주석 포함) | line 46 ✓ |
| Redis 블록 | `grep "redis:"` | 존재 | line 33 ✓ |
| Actuator health 노출 | `grep "health,info"` | 존재 | line 42 ✓ |
| V1 마이그레이션 수 | `find ... -name "V1__*.sql" \| wc -l` | 1 | 1 ✓ |
| V1 내용 | `grep -c "platform_sample\|BIGSERIAL PRIMARY KEY"` | ≥1 | 3 ✓ |
| compose 핵심 토큰 | `grep -c "postgres:16\|redis:7\|로컬 개발 전용\|healthcheck"` | ≥4 | 5 ✓ |
| compose healthcheck | `grep -n "healthcheck"` | 2개 (postgres+redis) | line 12, 23 ✓ |

전 항목 통과.

---

## 편차 (Deviations)

없음. 플랜 명세 그대로 구현.

비고:
- application.yml은 중첩(nested) YAML 형식을 사용했다 (`spring: threads: virtual: enabled: true`). 플랜이 flat/nested 모두 허용했으며 Spring Boot가 동일하게 해석한다.
- `management.endpoints.web.exposure.include`는 `health,info` (공백 없음)로 작성. RESEARCH 패턴 5의 `health, info`와 의미 동일.
- 테스트 파일(src/test/...)은 동시 실행 중인 01-02 에이전트 소관이므로 건드리지 않음.

---

## 후속 의존성

- 01-04(샘플 도메인/Mapper)가 `platform_sample` 테이블에 매핑되는 `SampleEntity`/`SampleMapper`를 main 소스셋에 배치한다.
- 01-02(테스트)의 Testcontainers 통합 테스트가 이 설정 + 마이그레이션을 검증한다.
- 앱/`docker compose`는 이번 단계에서 실행하지 않음 (플랜 명시).

*Phase: 01-platform-skeleton / Plan 01-03*
