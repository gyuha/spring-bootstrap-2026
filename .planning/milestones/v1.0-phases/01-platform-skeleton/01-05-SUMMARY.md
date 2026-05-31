# 01-05 최종 검증 게이트 — SUMMARY

**목표:** `./gradlew test` 전체 GREEN
**결과:** 달성 — 8개 테스트 전부 통과, 실패 0, 에러 0, 스킵 0

## 최종 `./gradlew test` 결과 (clean 빌드 기준)

```
BUILD SUCCESSFUL
```

| 테스트 클래스 | tests | failures | errors | skipped |
|---|---|---|---|---|
| ArchitectureTest | 1 | 0 | 0 | 0 |
| ActuatorHealthTest | 1 | 0 | 0 | 0 |
| FlywayMigrationTest | 2 | 0 | 0 | 0 |
| PersistenceIntegrationTest | 2 | 0 | 0 | 0 |
| VirtualThreadTest | 2 | 0 | 0 | 0 |
| **합계** | **8** | **0** | **0** | **0** |

`./gradlew clean test` 로 콜드 상태에서도 재현 확인 완료.

## 수정한 파일

1. `src/test/java/com/anchors/baseline/platform/infrastructure/PersistenceIntegrationTest.java`
   - `@Disabled` 제거, 본체 복원.
   - 실제 import 경로 사용: `SampleEntity`(domain.model), `SampleDto`(application), `SampleMapper`(infrastructure.mybatis).
   - `@Autowired EntityManager em`, `@Autowired SampleMapper sampleMapper`, `@Autowired StringRedisTemplate redisTemplate`.
   - `jpaWriteAndMyBatisReadInSingleTransaction()`: `em.persist` → `em.flush()` → `sampleMapper.findAll()` → value 가 `test-value-` 로 시작하는지 검증.
   - `redisConnectionAlive()`: Redis set/get 검증 (PLAT-05).

2. `src/test/java/com/anchors/baseline/platform/infrastructure/FlywayMigrationTest.java`
   - `flywayPlatformSampleTableExists()` 추가 — `information_schema.tables` 에서 `platform_sample` 카운트 == 1.
   - 기존 `flywaySchemaHistoryExists()` 의 `isGreaterThan(0)` → `isGreaterThanOrEqualTo(1)` (acceptance 문구 정합).

3. `src/test/java/com/anchors/baseline/platform/infrastructure/VirtualThreadTest.java`
   - 프로퍼티 검증 테스트를 plan 의 `@Value("${spring.threads.virtual.enabled:false}") boolean` + `springVirtualThreadsEnabled()` 형태로 정렬.
   - `virtualThreadEnabled()` 의 `isVirtual()` 검증 유지. `@Disabled` 없음.

4. `src/main/resources/application.yml`
   - `mybatis.configuration.map-underscore-to-camel-case: true` 추가.

5. `src/test/java/com/anchors/baseline/AbstractIntegrationTest.java` **(계획 외 — 근본 원인 수정)**
   - `@Testcontainers`/`@Container` (클래스 수명 결합) 제거, 싱글턴 컨테이너 패턴으로 전환 (static 초기화 블록 `start()`, 명시적 stop 없음).

6. `build.gradle.kts` **(계획 외 — 환경 호환성 수정)**
   - 테스트 태스크에 `systemProperty("api.version", "1.43")` + `environment("DOCKER_API_VERSION", "1.43")` 추가.

> ActuatorHealthTest 는 이미 plan 과 일치(`@Disabled` 없음, `/actuator/health` 200 + status UP + db + redis)하여 수정 불필요. ArchitectureTest 는 Wave 3 상태 그대로 GREEN — 미변경.

## 계획에서 벗어난 사항 (DEVIATIONS) 및 근본 원인 분석

### 1. Docker API 버전 불일치 (build.gradle.kts)

- **증상:** Testcontainers 가 `IllegalStateException: Could not find a valid Docker environment`. 세 전략(Environment/UnixSocket/DockerDesktop) 모두 `/info` 호출에서 HTTP 400(빈/제로 페이로드) 으로 실패.
- **근본 원인:** Docker Desktop 엔진 29.4.0 의 서버 API 는 1.54(min 1.40). Testcontainers 1.21.2 의 docker-java 가 협상하는 기본 API 버전이 데몬 최소(1.40) 미만이라, 데몬이 `/v1.3x/info` 에 대해 빈 `{"ID":"","Containers":0,...}` 페이로드를 반환 → docker-java 가 유효하지 않은 환경으로 판단. (CLI 는 정상 협상하므로 `docker info`/`docker ps` 는 동작.) `curl --unix-socket .../v1.40/info` 부터 정상 응답, `/v1.32/info` 는 빈 응답으로 직접 재현 확인.
- **수정:** 테스트 JVM 에 `api.version=1.43` system property + `DOCKER_API_VERSION=1.43` env 강제. (쉘 env 만으로는 fork 된 테스트 JVM 에 전파되지 않아 build.gradle.kts 에 고정.)

### 2. 다중 테스트 클래스 간 컨테이너 수명 충돌 (AbstractIntegrationTest)

- **증상:** 단일 클래스 실행(`--tests "*PersistenceIntegrationTest*"`)은 GREEN 이나, 전체 스위트 실행 시 PersistenceIntegrationTest 두 테스트가 Postgres `ConnectException`(Hikari 30s 타임아웃) + Redis `RedisConnectionFailureException` 으로 실패. 매핑 포트가 실행마다 상이(53375/53378/53397).
- **근본 원인:** `@Container` static 필드 + `@Testcontainers` 는 컨테이너 수명을 **테스트 클래스**에 묶어 클래스 종료 시 컨테이너를 stop 한다. 반면 Spring 테스트 컨텍스트는 동일 `@SpringBootTest`/`@ActiveProfiles` 설정의 여러 클래스 간 **캐시·공유**된다. 따라서 앞선 클래스가 끝나며 컨테이너를 중지하면, 캐시된 컨텍스트를 재사용하는 다음 클래스가 이미 중지된 컨테이너의 매핑 포트에 붙어 연결 거부. (shutdown 로그의 `Cannot reconnect to localhost:53482` 가 옛 포트 참조를 증명.)
- **수정:** 싱글턴 컨테이너 패턴 — `@Testcontainers`/`@Container` 제거, static 초기화 블록에서 한 번만 `start()`, 명시적 stop 없음(JVM 종료 + Ryuk 정리). `@ServiceConnection` 필드 기반 연결 주입은 `@Testcontainers` 없이도 동작. 전 클래스가 동일한 살아있는 컨테이너 공유.

두 수정 모두 테스트를 비활성화하지 않고 실제 근본 원인을 제거한 것이며, 검증을 약화시키지 않는다.
