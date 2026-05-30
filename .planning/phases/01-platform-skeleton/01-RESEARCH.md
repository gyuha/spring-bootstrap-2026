# Phase 1: 플랫폼 골격 - Research

**작성일:** 2026-05-30
**도메인:** Spring Boot 3.5 / JPA + MyBatis / PostgreSQL / Redis / Flyway / ArchUnit / Testcontainers
**신뢰도:** HIGH (핵심 스택 모두 Maven Central 버전 검증 완료)

---

<user_constraints>
## 사용자 제약사항 (CONTEXT.md 기반)

### 확정된 결정 (Locked Decisions)
- **D-01:** Gradle (Kotlin DSL) 단일 모듈. 컨텍스트 분리는 패키지로 표현, 멀티모듈 미채택.
- **D-02:** 루트 패키지 = `com.anchors.baseline`
- **D-03:** Phase 1은 `platform` / `common` 골격만 생성. `identity` / `authorization` 컨텍스트 디렉터리 선점유 금지. 패키지 계층 규약(`<context>/{domain,application,infrastructure,interfaces}`)은 platform/common에 적용.
- **D-04:** JPA(쓰기) + MyBatis(복잡 조회)가 동일 DataSource·단일 트랜잭션에서 동작함을 Testcontainers 통합 테스트로 증명. 검증 코드 유지(회귀 방지).
- **D-05:** ArchUnit 테스트로 `interfaces → application → domain` 의존 방향 강제. CI 게이트. Spring Modulith 미채택.
- **D-06:** 로컬 개발 = Docker Compose(PostgreSQL + Redis). 자동 검증 = Testcontainers. `/actuator/health`가 앱·DB·Redis 상태 반환.

### Claude 재량 영역 (Claude's Discretion)
- Gradle 플러그인/버전 카탈로그 구성
- Flyway 마이그레이션 파일 네이밍/디렉터리
- ArchUnit 규칙 세부 표현
- Testcontainers 검증 슬라이스의 정확한 패키지 위치
- `/actuator` health indicator 노출 범위 세부

### 연기된 아이디어 (OUT OF SCOPE)
- Spring Modulith (Phase 2~4에서 ArchUnit 대체/보완 여부 재검토)
- Identity / Authorization 실제 도메인 모델·유스케이스
</user_constraints>

---

<phase_requirements>
## Phase 요구사항

| ID | 설명 | Research 지원 |
|----|------|--------------|
| PLAT-01 | Java 21 + Spring Boot MVC 애플리케이션이 가상 스레드 활성화 상태로 기동한다 | §스택, §가상 스레드 설정, §시작 로그 검증 |
| PLAT-02 | 헥사고날 + DDD 패키지 구조(`<context>/{domain,application,infrastructure,interfaces}`)가 컨텍스트별로 잡혀 있고 의존 방향이 안쪽으로만 향한다 | §ArchUnit 규칙, §패키지 구조 |
| PLAT-03 | PostgreSQL 연결과 Flyway 마이그레이션으로 스키마가 버전 관리된다 | §Flyway 설정, §검증 방법 |
| PLAT-04 | JPA(쓰기/기본 조회)와 MyBatis(복잡 조회)가 동일 DataSource·단일 트랜잭션으로 동작한다 | §JPA+MyBatis 공존, §단일 트랜잭션 패턴, §Testcontainers 통합 테스트 |
| PLAT-05 | Redis 연결이 세션 저장소로 구성된다 | §Redis 설정, §헬스체크 검증 |
| PLAT-06 | 헬스체크 엔드포인트로 앱·DB·Redis 상태를 확인할 수 있다 | §Actuator health 설정 |
</phase_requirements>

---

## 요약

Phase 1은 Spring Boot 3.5 위에서 헥사고날 + DDD 패키지 골격, PostgreSQL + Flyway, JPA + MyBatis 단일 트랜잭션 결합, Redis, Actuator 헬스체크를 동작 상태로 세우는 작업이다. 코드는 하나도 없는 greenfield 프로젝트이므로 처음부터 올바른 구조를 잡는 것이 핵심이다.

세 가지 특이 지점이 있다. 첫째, JPA + MyBatis 단일 트랜잭션 결합은 `JpaTransactionManager` 하나로 두 프레임워크를 묶되, **JPA `persist()` 이후 반드시 `flush()`를 호출**해야 같은 트랜잭션 내에서 MyBatis 쿼리가 변경 내용을 볼 수 있다. 둘째, Flyway 10+ 부터 PostgreSQL 지원이 `flyway-core`에서 분리되어 `flyway-database-postgresql` 의존성을 별도로 추가해야 한다. 셋째, ArchUnit의 `onionArchitecture()` API는 이 프로젝트의 패키지 구조와 완벽하게 일치하지 않으므로, `layeredArchitecture()` + 커스텀 규칙 조합이 더 적합하다.

**핵심 권고사항:** 단일 `JpaTransactionManager`를 선언하고 MyBatis `SqlSessionFactory`가 동일 `DataSource`를 참조하게 하라. 트랜잭션 검증 테스트는 JPA persist → flush → MyBatis select → 검증 패턴으로 작성하라.

---

## 아키텍처 책임 맵

| 기능 | 주요 계층 | 보조 계층 | 근거 |
|------|----------|----------|------|
| HTTP 요청 수신 / 응답 반환 | `interfaces` (REST Controller) | — | 헥사고날 인바운드 어댑터 |
| 유스케이스 조율 | `application` (Application Service) | — | 트랜잭션 경계 보유 |
| 도메인 불변식 | `domain` (Aggregate, VO) | — | 순수 도메인; 프레임워크 의존 금지 |
| JPA 영속화 | `infrastructure` (JPA Repository impl) | `domain` (Repository interface) | 포트/어댑터 패턴 |
| MyBatis 복잡 조회 | `infrastructure` (Mapper) | `application` (QueryService) | 읽기 모델 |
| Flyway 스키마 마이그레이션 | `infrastructure` (resources/db/migration) | — | Spring Boot 자동 실행 |
| Redis 연결 설정 | `infrastructure` (configuration) | — | Phase 1: 연결만; BFF 세션은 Phase 3 |
| 헬스체크 | Spring Boot Actuator | — | 프레임워크 제공, 커스텀 불필요 |
| 의존 방향 강제 | ArchUnit 테스트 (test 소스셋) | — | CI 게이트 역할 |

---

## 표준 스택

### Core
| 라이브러리 | 버전 | 목적 | 선택 근거 |
|-----------|------|------|----------|
| Spring Boot | **3.5.3** | 애플리케이션 프레임워크 | Maven Central 최신 안정 3.x [VERIFIED: Maven Central] |
| Java | **21** (LTS) | 런타임, 가상 스레드 | 베이스라인 §2 고정 [CITED: baseline §2] |
| Spring Data JPA (Hibernate) | BOM 관리 | 쓰기/기본 조회 | 베이스라인 §2 고정 [CITED: baseline §2] |
| mybatis-spring-boot-starter | **3.0.4** | 복잡 조회 (CQRS-lite) | Spring Boot 3.2~3.5 지원 라인 [VERIFIED: Maven Central] |
| flyway-core | **11.8.2** | 스키마 버전 관리 | Spring Boot 3.5 BOM 관리 [VERIFIED: Maven Central] |
| flyway-database-postgresql | **11.8.2** | PostgreSQL 드라이버 분리 모듈 | Flyway 10+부터 별도 의존성 필수 [VERIFIED: Maven Central] |
| spring-boot-starter-data-redis | BOM 관리 | Redis 연결, 헬스 인디케이터 | 베이스라인 §2 고정 [CITED: baseline §2] |
| spring-boot-starter-actuator | BOM 관리 | 헬스체크 엔드포인트 | PLAT-06 요구사항 [CITED: REQUIREMENTS.md] |
| Lombok | **1.18.38** | 보일러플레이트 절감 | 베이스라인 §2 고정 [VERIFIED: Maven Central] |

### Supporting (테스트)
| 라이브러리 | 버전 | 목적 | 사용 조건 |
|-----------|------|------|---------|
| archunit-junit5 | **1.4.1** | 의존 방향 강제 (D-05) | 모든 테스트에 포함 [VERIFIED: Maven Central] |
| spring-boot-testcontainers | BOM 관리 | @ServiceConnection 통합 | Spring Boot 3.1+ 기능 [VERIFIED: Maven Central] |
| org.testcontainers:postgresql | **1.21.3** | Testcontainers PostgreSQL | D-04 검증 [VERIFIED: Maven Central] |
| org.testcontainers:junit-jupiter | **1.21.3** | @Testcontainers / @Container | JUnit 5 통합 [VERIFIED: Maven Central] |

### 대안 고려 사항
| 대신 | 대안 | 트레이드오프 |
|------|------|------------|
| mybatis-spring-boot-starter 3.0.4 | 4.0.0 | 4.x는 Spring Boot 4.0 전용 — 현재 프로젝트에서 사용 불가 |
| JpaTransactionManager | DataSourceTransactionManager | JPA 없이 순수 JDBC 프로젝트에서만 적합 |
| lettuce-core (기본) | jedis | Spring Boot 기본값이 Lettuce; Phase 1에서는 변경 불필요 |
| archunit-junit5 | Spring Modulith | 연기됨 (DEFERRED) |

**설치 (Gradle Kotlin DSL `build.gradle.kts`):**
```kotlin
dependencies {
    // 웹 + JPA
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // MyBatis (Spring Boot 3.x 호환 라인)
    implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4")
    
    // Flyway (PostgreSQL은 별도 모듈 필수)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    // Redis + Actuator
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    // PostgreSQL 드라이버
    runtimeOnly("org.postgresql:postgresql")
    
    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}
```

---

## Package Legitimacy Audit

> 이 Phase는 Java/Maven 생태계 패키지를 사용한다. slopcheck는 Python PyPI 기준으로 동작하므로 Maven 패키지에 적용 불가 (cross-ecosystem). Maven Central 직접 검증으로 대체.

| 패키지 | Registry | 검증 버전 | 공식 출처 | slopcheck | 처리 |
|--------|---------|---------|---------|-----------|------|
| spring-boot-starter-* | Maven Central | 3.5.3 | spring.io/projects/spring-boot | N/A (Java) | 승인 |
| mybatis-spring-boot-starter | Maven Central | 3.0.4 | mybatis.org/spring-boot-starter | N/A (Java) | 승인 |
| flyway-core | Maven Central | 11.8.2 | flywaydb.org | N/A (Java) | 승인 |
| flyway-database-postgresql | Maven Central | 11.8.2 | flywaydb.org | N/A (Java) | 승인 |
| archunit-junit5 | Maven Central | 1.4.1 | archunit.org | N/A (Java) | 승인 |
| testcontainers:postgresql | Maven Central | 1.21.3 | testcontainers.org | N/A (Java) | 승인 |
| testcontainers:junit-jupiter | Maven Central | 1.21.3 | testcontainers.org | N/A (Java) | 승인 |
| spring-boot-testcontainers | Maven Central | 3.5.3 | spring.io/spring-boot | N/A (Java) | 승인 |

**slopcheck [SLOP] 제거 패키지:** 없음
**주의 패키지 [SUS]:** 없음

*slopcheck는 PyPI 전용으로 Java Maven 패키지에 적용되지 않음. Maven Central 직접 조회 + 공식 문서 교차 검증으로 모든 패키지를 `[VERIFIED: Maven Central]`로 처리.*

---

## 아키텍처 패턴

### 시스템 아키텍처 다이어그램

HTTP 요청은 `interfaces` 계층(Controller)에서 진입해 `application` 계층(ApplicationService)에서 유스케이스를 조율한다. 쓰기 작업은 `domain` 계층(Aggregate)을 통해 `infrastructure` 계층(JPA Repository)에서 영속화되고, 복잡 조회는 `infrastructure` 계층(MyBatis Mapper)이 직접 DB를 조회해 읽기 DTO를 반환한다. 두 경로 모두 `application` 계층의 `@Transactional` 경계 안에서 단일 `JpaTransactionManager`에 의해 하나의 트랜잭션으로 처리된다.

```
HTTP Request
    ↓
[interfaces] Controller (REST)
    ↓ @Transactional 시작 (JpaTransactionManager)
[application] ApplicationService / QueryService
    ↓ 쓰기                      ↓ 복잡 조회
[domain] Aggregate (JPA)    [infrastructure] MyBatis Mapper
    ↓                              ↓
[infrastructure] JPA Repository impl
    ↓                              ↓
              PostgreSQL (단일 DataSource)
              
[infrastructure] RedisConnectionFactory → Redis
[/actuator/health] → DB + Redis + App 상태 집계
```

```mermaid
graph TD
    HTTP[HTTP Request] --> CTL[interfaces / Controller]
    CTL -->|@Transactional| AS[application / ApplicationService]
    CTL --> QS[application / QueryService]
    AS --> AGG[domain / Aggregate]
    AGG --> JPAREPO[infrastructure / JPA Repository impl]
    QS --> MAPPER[infrastructure / MyBatis Mapper]
    JPAREPO --> PG[(PostgreSQL\n단일 DataSource)]
    MAPPER --> PG
    
    REDIS[infrastructure / Redis Config] --> REDISDB[(Redis)]
    ACT[/actuator/health] --> PG
    ACT --> REDISDB

    style CTL fill:#4a90d9,color:#fff
    style AS fill:#7b68ee,color:#fff
    style QS fill:#7b68ee,color:#fff
    style AGG fill:#27ae60,color:#fff
    style JPAREPO fill:#e67e22,color:#fff
    style MAPPER fill:#e67e22,color:#fff
    style PG fill:#2c3e50,color:#fff
    style REDISDB fill:#c0392b,color:#fff
```

### 권장 프로젝트 구조
```
src/
├── main/
│   ├── java/com/anchors/baseline/
│   │   ├── BaselineApplication.java          # @SpringBootApplication
│   │   ├── platform/                         # platform 바운디드 컨텍스트
│   │   │   ├── domain/
│   │   │   │   └── model/                    # 샘플 도메인 모델 (Phase 1 골격용)
│   │   │   ├── application/                  # 유스케이스 조율
│   │   │   ├── infrastructure/
│   │   │   │   ├── jpa/                      # JPA 리포지토리 구현
│   │   │   │   ├── mybatis/                  # MyBatis Mapper
│   │   │   │   └── config/                   # DataSource, Redis 설정 (필요 시)
│   │   │   └── interfaces/
│   │   │       └── rest/                     # REST Controller
│   │   └── common/                           # 공통 유틸리티, 공유 VO
│   │       ├── domain/
│   │       ├── application/
│   │       ├── infrastructure/
│   │       └── interfaces/
│   └── resources/
│       ├── application.yml
│       ├── db/
│       │   └── migration/
│       │       └── V1__init_schema.sql       # Flyway 초기 마이그레이션
│       └── mybatis/
│           └── mapper/                       # MyBatis XML Mapper (선택)
└── test/
    ├── java/com/anchors/baseline/
    │   ├── architecture/
    │   │   └── ArchitectureTest.java          # ArchUnit CI 게이트
    │   └── platform/
    │       └── infrastructure/
    │           └── PersistenceIntegrationTest.java  # D-04 검증
    └── resources/
        └── application-test.yml
```

---

## 핵심 구현 패턴

### 패턴 1: 가상 스레드 활성화 (PLAT-01)

**설명:** `application.yml`에 한 줄로 활성화. Spring Boot 3.2+부터 Tomcat 가상 스레드 executor, `@Async` 메서드, Spring MVC 비동기 처리 모두 자동 설정된다.

```yaml
# application.yml
spring:
  threads:
    virtual:
      enabled: true
```

**가상 스레드 활성화 확인 방법 (SC#1):**
```java
// 컨트롤러 또는 CommandLineRunner에서 확인
@GetMapping("/debug/thread")
public String threadInfo() {
    Thread t = Thread.currentThread();
    return String.format("name=%s, virtual=%s", t.getName(), t.isVirtual());
}
```

또는 `CommandLineRunner`로 시작 시 로그 출력:
```java
@Bean
CommandLineRunner verifyVirtualThreads() {
    return args -> {
        Thread vt = Thread.ofVirtual().start(() ->
            log.info("Virtual thread active: {}", Thread.currentThread().isVirtual())
        );
        vt.join();
    };
}
```

`Thread.currentThread().isVirtual()` 반환값이 `true`이면 SC#1 충족. [CITED: spring.io/blog, baeldung.com/spring-6-virtual-threads]

---

### 패턴 2: JPA + MyBatis 단일 트랜잭션 (PLAT-04 / D-04)

**핵심 원칙:** `JpaTransactionManager` 하나로 JPA EntityManager와 MyBatis SqlSession 모두를 관리한다. MyBatis-Spring은 Spring Transaction에 위임하므로 동일 DataSource에 연결된 `SqlSessionFactory`는 JPA 트랜잭션에 자동 참여한다.

**Spring Boot 자동설정 동작:**
- `spring-boot-starter-data-jpa` + `mybatis-spring-boot-starter` 동시 존재 시 Spring Boot는 `JpaTransactionManager`를 primary로 등록한다.
- `mybatis-spring-boot-starter`는 감지된 `DataSource`로 `SqlSessionFactory`와 `SqlSessionTemplate`을 자동 등록한다.
- **추가 설정 없이 단일 DataSource면 자동으로 공유**된다. [VERIFIED: Maven Central / mybatis.org docs]

**명시적 설정 (Spring Boot 자동설정이 충분하지 않을 경우):**
```java
// infrastructure/config/PersistenceConfig.java
@Configuration
public class PersistenceConfig {
    
    // Spring Boot가 자동 생성하지만, 명시적으로 선언해 혼동 방지
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }
}
```

**핵심 함정 — `flush()` 없는 단일 트랜잭션 내 JPA→MyBatis 조회:**
JPA `persist()` 후 flush 없이 같은 트랜잭션에서 MyBatis로 조회하면 **아직 DB에 기록되지 않은 데이터**를 읽으므로 조회 결과가 비어있다.
```java
// 잘못된 방법
em.persist(entity);
// MyBatis 쿼리 → 빈 결과 (flush되지 않아서)
List<Dto> result = mapper.findAll();

// 올바른 방법  
em.persist(entity);
em.flush(); // ← 필수: persistence context → DB에 즉시 반영
List<Dto> result = mapper.findAll(); // ← 이제 읽힌다
```

[CITED: thecodinglog.github.io/jpa/mybatis/spring/2019/09/11]

---

### 패턴 3: Flyway 설정 (PLAT-03)

**Flyway 10+ 이후 변경점:** `flyway-core`에서 DB 벤더별 드라이버가 분리되었다. PostgreSQL은 `flyway-database-postgresql`을 별도로 추가해야 한다. 없으면 `FlywayException: No database found to handle jdbc:postgresql://...` 에러 발생. [CITED: x.com/simas_ch/status/1793965273479082139, dev-solve.com]

```yaml
# application.yml
spring:
  flyway:
    locations: classpath:db/migration
    baseline-on-migrate: false   # greenfield 프로젝트 — 기존 DB 없으므로 false
    validate-on-migrate: true
    # spring.flyway.enabled: true 가 기본값 — 명시 불필요
```

**마이그레이션 파일 네이밍 규약:**
```
src/main/resources/db/migration/
  V1__init_schema.sql         ← V{버전}__{설명}.sql (언더스코어 2개)
  V2__add_platform_table.sql
```

**SC#3 증명:** 앱 기동 후 `flyway_schema_history` 테이블에 `success = true` 레코드 확인.

---

### 패턴 4: Redis 연결 설정 (PLAT-05)

Phase 1은 BFF 세션 로직 없이 **연결 확립 + 헬스체크** 증명만 필요하다.

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # Phase 1: 비밀번호 없음 (로컬 개발)
      # lettuce 클라이언트가 기본 사용됨
```

```yaml
# application-test.yml (Testcontainers 오버라이드용)
# @ServiceConnection이 자동으로 host/port를 덮어쓰므로 별도 설정 불필요
```

---

### 패턴 5: Actuator 헬스체크 (PLAT-06)

Spring Boot Actuator는 `DataSource`(PostgreSQL)와 Redis 클라이언트가 클래스패스에 있으면 **자동으로** `db`와 `redis` 헬스 인디케이터를 등록한다. 별도 코드 불필요.

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health, info
  endpoint:
    health:
      show-details: always   # 개발 환경: 전체 상세 노출
      show-components: always
  health:
    db:
      enabled: true
    redis:
      enabled: true
    diskspace:
      enabled: false   # Phase 1: 불필요
```

**SC#5 증명 응답 구조:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": { "database": "PostgreSQL", "validationQuery": "isValid()" }
    },
    "redis": {
      "status": "UP",
      "details": { "version": "7.x.x" }
    }
  }
}
```

---

### 패턴 6: ArchUnit 의존 방향 강제 (PLAT-02 / D-05)

**선택: `layeredArchitecture()` + 커스텀 규칙 조합**

ArchUnit의 `onionArchitecture()` API는 `adapter` 개념을 사용하는 반면, 이 프로젝트의 패키지 구조는 `infrastructure`와 `interfaces`가 별도로 존재한다. `layeredArchitecture()`로 레이어를 직접 정의하는 것이 더 명시적이다. [CITED: archunit.org/userguide]

```java
// test/java/com/anchors/baseline/architecture/ArchitectureTest.java

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class ArchitectureTest {

    private static final String ROOT = "com.anchors.baseline";

    JavaClasses classes = new ClassFileImporter()
        .importPackages(ROOT);

    /**
     * 계층 의존 방향 강제:
     *   interfaces → application → domain
     *   infrastructure → domain (포트 구현)
     *   infrastructure → application (포트 정의 참조)
     */
    @Test
    void hexagonalLayerDependencies() {
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain").definedBy(ROOT + ".(*).domain..")
            .layer("Application").definedBy(ROOT + ".(*).application..")
            .layer("Infrastructure").definedBy(ROOT + ".(*).infrastructure..")
            .layer("Interfaces").definedBy(ROOT + ".(*).interfaces..")
            // domain은 아무 계층도 의존하지 않는다
            .whereLayer("Domain").mayNotAccessAnyLayer()
            // application은 domain만 의존 가능
            .whereLayer("Application").mayOnlyAccessLayers("Domain")
            // interfaces는 application과 domain만 의존 가능 (domain은 VO/DTO 참조용)
            .whereLayer("Interfaces").mayOnlyAccessLayers("Application", "Domain")
            // infrastructure는 domain과 application만 의존 가능 (포트 구현)
            .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application")
            .check(classes);
    }

    /**
     * infrastructure는 domain interfaces를 구현해야 한다는 규칙 (선택 강화)
     * domain.repository 인터페이스가 infrastructure 밖에서 직접 구현되면 실패
     */
    @Test
    void domainRepositoriesImplementedInInfrastructure() {
        ArchRule rule = ArchRuleDefinition.classes()
            .that().resideInAPackage(ROOT + ".(*).domain.repository..")
            .and().areInterfaces()
            .should().onlyHaveFullyQualifiedName(ROOT + ".(*).domain.repository.(*)")
            // 실제 구현은 infrastructure에 있어야 함
            .orShould().haveSimpleNameEndingWith("Port"); // Port 인터페이스 패턴 허용
        rule.check(classes);
    }
}
```

**주의:** `mayNotAccessAnyLayer()` 대신 `mayOnlyBeAccessedByLayers()`를 방향 제어에 사용하면 더 직관적일 수 있다. 구현 시 ArchUnit 1.4 API 문서에서 정확한 메서드명 확인 필요. [ASSUMED]

---

### 패턴 7: Testcontainers 통합 테스트 — 단일 트랜잭션 검증 (D-04)

```java
// test/.../platform/infrastructure/PersistenceIntegrationTest.java

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PersistenceIntegrationTest {

    // PostgreSQL: @ServiceConnection으로 DataSource 자동 연결
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:16");

    // Redis: @ServiceConnection으로 Redis 자동 연결
    @Container
    @ServiceConnection
    static GenericContainer<?> redis =
        new GenericContainer<>("redis:7")
            .withExposedPorts(6379);
    
    // redis GenericContainer는 name 속성 명시 필요
    // 또는 com.redis.testcontainers:testcontainers-redis의 RedisContainer 사용

    @Autowired
    EntityManager em;
    
    @Autowired
    SampleMapper sampleMapper; // MyBatis Mapper

    @Autowired
    PlatformTransactionManager txManager;

    /**
     * SC#4: JPA 쓰기 + MyBatis 조회가 단일 트랜잭션에서 원자적으로 동작함을 증명
     */
    @Test
    @Transactional
    void jpaWriteAndMyBatisReadInSingleTransaction() {
        // 1) JPA로 엔티티 저장
        SampleEntity entity = new SampleEntity("test-value");
        em.persist(entity);
        em.flush(); // ← 필수: MyBatis가 읽을 수 있도록 즉시 flush

        // 2) 같은 트랜잭션에서 MyBatis로 조회
        List<SampleDto> results = sampleMapper.findAll();

        // 3) 동일 트랜잭션 내에서 방금 저장한 데이터가 보여야 한다
        assertThat(results).extracting(SampleDto::getValue)
            .contains("test-value");
    }
}
```

**Redis `@ServiceConnection` 주의:** `GenericContainer`는 `@ServiceConnection(name = "redis")`로 name을 명시해야 Spring Boot가 타입을 추론할 수 있다. 또는 `com.redis.testcontainers:testcontainers-redis` 라이브러리의 `RedisContainer`를 사용하면 `@ServiceConnection`만으로 자동 연결된다. [CITED: docs.spring.io/spring-boot/reference/testing/testcontainers.html]

```java
// GenericContainer + name 명시 패턴
@Container
@ServiceConnection(name = "redis")
static GenericContainer<?> redis =
    new GenericContainer<>("redis:7").withExposedPorts(6379);
```

---

### 패턴 8: Gradle Kotlin DSL + Version Catalog

**`gradle/libs.versions.toml`:**
```toml
[versions]
spring-boot = "3.5.3"
mybatis-spring-boot = "3.0.4"
archunit = "1.4.1"
testcontainers = "1.21.3"

[libraries]
# MyBatis (Spring Boot BOM 관리 밖)
mybatis-spring-boot-starter = { module = "org.mybatis.spring.boot:mybatis-spring-boot-starter", version.ref = "mybatis-spring-boot" }

# ArchUnit
archunit-junit5 = { module = "com.tngtech.archunit:archunit-junit5", version.ref = "archunit" }

# Testcontainers (Spring Boot BOM 이후 버전 관리되지만 명시적으로)
testcontainers-postgresql = { module = "org.testcontainers:postgresql" }
testcontainers-junit-jupiter = { module = "org.testcontainers:junit-jupiter" }

[plugins]
spring-boot = { id = "org.springframework.boot", version.ref = "spring-boot" }
spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.7" }
```

**`build.gradle.kts`:**
```kotlin
plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.anchors"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    
    implementation(libs.mybatis.spring.boot.starter)
    
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    
    runtimeOnly("org.postgresql:postgresql")
    
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

---

### 패턴 9: Docker Compose (로컬 개발)

**`compose.yaml`:**
```yaml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: baseline
      POSTGRES_USER: baseline
      POSTGRES_PASSWORD: baseline
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U baseline -d baseline"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s

  redis:
    image: redis:7
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3
```

**Spring Boot Docker Compose 자동 통합 (선택):**
`spring-boot-docker-compose` 의존성을 추가하면 Spring Boot가 `compose.yaml`을 자동 감지하고 `docker compose up`을 실행한다. Phase 1에서는 선택 사항이다. [CITED: spring.io/blog/2023/06/21/docker-compose-support-in-spring-boot-3-1]

---

### 안티패턴 주의

- **DataSource를 두 개 선언하지 말라:** JPA용 DataSource와 MyBatis용 DataSource를 각각 만들면 별도 트랜잭션이 생성되어 원자성이 깨진다. DataSource는 하나여야 한다.
- **`DataSourceTransactionManager`를 primary로 선언하지 말라:** JPA 환경에서 `JpaTransactionManager`가 primary여야 한다. `DataSourceTransactionManager`를 primary로 지정하면 JPA 1차 캐시(EntityManager)와 트랜잭션이 분리되어 예측 불가한 동작이 발생한다.
- **MyBatis Mapper를 `@Autowired`로 `@SpringBootApplication` 패키지 밖에 두지 말라:** `@MapperScan` 또는 `@Mapper`를 각 Mapper 인터페이스에 붙여야 auto-scan된다.
- **`flyway-database-postgresql` 없이 Flyway 10+ 사용하지 말라:** `FlywayException: No database found` 에러가 발생한다.
- **ArchUnit 테스트에서 레이어 패턴에 `(*)` 와일드카드를 사용할 때:** ArchUnit의 패키지 패턴 `(..)` 은 0개 이상의 패키지를 매칭한다. `com.anchors.baseline.(*).domain..` 에서 `(*)`는 단일 패키지 세그먼트만 매칭한다. 중첩 패키지(`platform.domain.model`)까지 포함하려면 `..domain..`을 사용하라. [ASSUMED — ArchUnit 패턴 문법 검증 필요]

---

## 직접 만들지 말아야 할 것 (Don't Hand-Roll)

| 문제 | 직접 만들지 말 것 | 대신 사용할 것 | 이유 |
|------|-----------------|--------------|------|
| 스키마 버전 관리 | 직접 DDL 실행 스크립트 | Flyway | 재현 불가능 마이그레이션, 팀 동기화 문제 |
| 트랜잭션 결합 | 수동 connection/session 공유 | JpaTransactionManager + MyBatis-Spring | 스레드 바인딩·롤백 복잡성 |
| 의존 방향 강제 | 코드 리뷰만으로 | ArchUnit | 리뷰는 놓칠 수 있음; CI 자동 게이트 필요 |
| 통합 테스트 DB | H2 인메모리 | Testcontainers + PostgreSQL | H2와 PostgreSQL 방언 차이 (JSON, CTE, 특수 타입) |
| Redis 헬스 모니터링 | 수동 커넥션 확인 | Actuator RedisHealthIndicator | 자동 감지, 표준 응답 포맷 |
| 가상 스레드 executor 설정 | TomcatProtocolHandlerCustomizer 빈 | `spring.threads.virtual.enabled=true` | Spring Boot 3.2+에서 한 줄로 완전 자동화 |

**핵심 통찰:** Spring Boot 자동설정이 이 스택의 결합(JPA + MyBatis, DataSource + TransactionManager, Actuator HealthIndicators)을 대부분 처리한다. 수동 Bean 선언은 디버깅 중 명시성이 필요할 때 한정으로 사용하라.

---

## 흔한 함정

### 함정 1: Flyway PostgreSQL 모듈 누락
**무슨 일이 발생하나:** `FlywayException: No database found to handle jdbc:postgresql://...`로 앱 기동 실패.
**원인:** Flyway 10부터 DB 벤더별 모듈이 `flyway-core`에서 분리됨.
**예방법:** `flyway-database-postgresql`을 `flyway-core`와 함께 반드시 추가.
**조기 감지:** 앱 기동 시 Flyway 관련 `ClassNotFoundException`을 확인.

### 함정 2: JPA flush 없는 MyBatis 조회
**무슨 일이 발생하나:** JPA `persist()` 직후 같은 트랜잭션에서 MyBatis 조회 시 빈 결과 반환.
**원인:** JPA persistence context는 flush 전까지 변경사항을 메모리에만 보관. MyBatis는 직접 JDBC를 통해 DB를 조회하므로 flush되지 않은 데이터를 볼 수 없다.
**예방법:** JPA → MyBatis 순서로 사용할 때는 중간에 `em.flush()` 호출.
**조기 감지:** 통합 테스트(D-04)가 이 패턴을 명시적으로 검증한다.

### 함정 3: mybatis-spring-boot-starter 4.0.0 사용
**무슨 일이 발생하나:** Spring Boot 3.5와 4.0.0을 같이 사용하면 의존성 충돌 또는 기능 오동작.
**원인:** 4.0.0은 Spring Boot 4.x 전용이다.
**예방법:** Spring Boot 3.5에서는 반드시 `mybatis-spring-boot-starter:3.0.4` 사용.
**조기 감지:** Maven Central 릴리스 노트에서 버전 호환성 테이블 확인.

### 함정 4: ArchUnit layeredArchitecture + 순환 의존
**무슨 일이 발생하나:** `domain`이 `application`을 참조하거나, `infrastructure`가 `interfaces`를 참조하면 ArchUnit 규칙 실패.
**원인:** Spring `@Autowired`로 상위 계층 빈을 하위 계층에서 주입하면 방향이 역전됨.
**예방법:** DIP(의존성 역전 원칙)으로 인터페이스를 domain에 정의하고 infrastructure에서 구현.
**조기 감지:** ArchUnit 테스트를 Wave 0에서 먼저 작성하고 초기 골격 생성 시 즉시 실행.

### 함정 5: `@ServiceConnection`이 없는 `GenericContainer` Redis
**무슨 일이 발생하나:** Testcontainers Redis 컨테이너가 올라가도 Spring Boot가 연결 정보를 주입하지 못해 `application.yml`의 `localhost:6379`로 연결 시도 → 컨테이너 포트 충돌 또는 연결 실패.
**원인:** `GenericContainer`는 타입 추론이 안 되므로 `@ServiceConnection(name = "redis")` name 속성 필수.
**예방법:** `@ServiceConnection(name = "redis")` 또는 `RedisContainer` 사용.

### 함정 6: 가상 스레드 + HikariCP
**무슨 일이 발생하나:** 가상 스레드 환경에서 HikariCP 기본 `connectionTimeout`(30초)보다 짧은 작업이 pool exhaustion을 일으킬 수 있다.
**원인:** 가상 스레드는 플랫폼 스레드보다 훨씬 많은 동시 요청을 처리하려 하므로 pool 크기를 초과한다.
**예방법:** Phase 1(골격)에서는 기본값으로 시작. 부하 테스트 시 `spring.datasource.hikari.maximum-pool-size`를 조정하거나 HikariCP의 `keepaliveTime` 설정 검토. 베이스라인 §NFR-04(동시성) 요구사항이므로 Phase 1 이후 검토 권장. [ASSUMED — 정확한 최적값은 부하 패턴에 따라 다름]

---

## 환경 가용성

| 의존성 | 필요한 기능 | 가용 여부 | 버전 | 폴백 |
|-------|-----------|---------|------|------|
| Java 21 | 가상 스레드, 전체 스택 | ✓ | OpenJDK 21.0.7 (Temurin LTS) | — |
| Gradle | 빌드 | ✓ | 9.5.1 | — |
| Docker | Docker Compose 로컬 개발, Testcontainers | ✓ | 29.4.0 | — |
| Docker Compose | 로컬 PostgreSQL + Redis | ✓ | v5.1.2 | — |
| PostgreSQL (컨테이너) | Flyway 마이그레이션, JPA, MyBatis | ✓ | Docker 이미지로 제공 | — |
| Redis (컨테이너) | 세션 저장소, 헬스체크 | ✓ | Docker 이미지로 제공 | — |

**폴백 없는 누락 의존성:** 없음. 모든 필수 런타임 도구가 로컬에 설치되어 있음.

---

## 검증 아키텍처 (Nyquist Validation)

### 테스트 프레임워크

| 속성 | 값 |
|------|-----|
| 프레임워크 | JUnit 5 (spring-boot-starter-test에 포함) |
| 설정 파일 | `build.gradle.kts` (`useJUnitPlatform()`) |
| 빠른 실행 명령 | `./gradlew test --tests "com.anchors.baseline.architecture.*"` |
| 전체 스위트 명령 | `./gradlew test` |

### 요구사항 → 테스트 매핑

| 요구사항 ID | 동작 | 테스트 유형 | 자동화 명령 | 파일 존재 여부 |
|------------|------|-----------|-----------|--------------|
| PLAT-01 | 가상 스레드 활성화 확인 | 통합 (수동 또는 smoke) | `./gradlew test --tests "*VirtualThreadTest*"` | ❌ Wave 0 |
| PLAT-02 | 패키지 의존 방향 위반 없음 | 아키텍처 (ArchUnit) | `./gradlew test --tests "*ArchitectureTest*"` | ❌ Wave 0 |
| PLAT-03 | Flyway 마이그레이션 실행 및 `flyway_schema_history` 확인 | 통합 (Testcontainers) | `./gradlew test --tests "*FlywayMigrationTest*"` | ❌ Wave 0 |
| PLAT-04 | JPA 쓰기 + MyBatis 조회 단일 트랜잭션 원자성 | 통합 (Testcontainers) | `./gradlew test --tests "*PersistenceIntegrationTest*"` | ❌ Wave 0 |
| PLAT-05 | Redis 연결 확인 | 통합 (Testcontainers) | `./gradlew test --tests "*PersistenceIntegrationTest*"` | ❌ Wave 0 |
| PLAT-06 | `/actuator/health`가 `db` + `redis` UP 반환 | 통합 (Testcontainers) | `./gradlew test --tests "*ActuatorHealthTest*"` | ❌ Wave 0 |

### Wave 0 갭 (구현 전 생성 필요)

- [ ] `src/test/java/com/anchors/baseline/architecture/ArchitectureTest.java` — PLAT-02 (ArchUnit)
- [ ] `src/test/java/com/anchors/baseline/platform/infrastructure/PersistenceIntegrationTest.java` — PLAT-04 (JPA+MyBatis 단일 트랜잭션)
- [ ] `src/test/java/com/anchors/baseline/platform/infrastructure/ActuatorHealthTest.java` — PLAT-06
- [ ] `src/test/resources/application-test.yml` — Testcontainers 전용 프로파일 설정
- [ ] 공통 `AbstractIntegrationTest.java` (base class) — @Testcontainers + @ServiceConnection 컨테이너 공유

---

## 보안 도메인

> `security_enforcement: true` (config.json), ASVS level 1 적용. Phase 1은 인증/인가 도메인 없음. 플랫폼 골격에 해당하는 항목만 검토.

### 적용 가능한 ASVS 카테고리

| ASVS 카테고리 | 적용 여부 | 표준 제어 |
|-------------|---------|---------|
| V2 인증 | 아니오 (Phase 3) | — |
| V3 세션 관리 | 부분 (Redis 연결 설정만) | Redis는 로컬 only; Phase 3에서 BFF 세션 토큰 보안 처리 |
| V4 접근 제어 | 아니오 (Phase 4) | — |
| V5 입력 유효성 검사 | 제한적 | Flyway SQL 마이그레이션은 버전 관리 소스이므로 외부 입력 아님 |
| V6 암호화 | 아니오 | Phase 1에서 자격증명 없음 (로컬 Docker Compose) |

### Phase 1 특정 보안 주의 사항

| 패턴 | STRIDE | 표준 완화 |
|------|--------|---------|
| Actuator 엔드포인트 노출 | 정보 노출 | `show-details: always`는 개발 환경 전용. 운영은 `when_authorized`로 변경 필요 [ASSUMED — 베이스라인에 운영 설정 미명시] |
| Docker Compose 자격증명 | 정보 노출 | `compose.yaml`의 패스워드는 로컬 개발 전용 — `.gitignore` 제외 불필요하나 명시적 주석 권장 |

---

## 가정 로그

| # | 주장 | 섹션 | 틀릴 경우 영향 |
|---|------|------|-------------|
| A1 | ArchUnit `layeredArchitecture()` 패턴 문자열에서 `(*)`가 단일 세그먼트만 매칭하므로 `..domain..`을 사용해야 한다 | ArchUnit 규칙 패턴 | 의존성 규칙이 일부 패키지를 놓치거나 잘못 감지할 수 있음 — 실행 시 확인 필요 |
| A2 | `management.endpoint.health.show-details: always`는 개발 환경 전용이며 운영 배포 전 변경이 필요하다 | Actuator 설정 | 운영 환경에서 DB 내부 정보 노출 위험 |
| A3 | 가상 스레드 활성화 시 HikariCP pool exhaustion이 발생할 수 있으므로 Phase 1 이후 pool 크기 조정을 검토해야 한다 | 함정 6 | Phase 1 골격 수준에서는 영향 없음; 실제 부하 발생 후 문제 |
| A4 | mybatis-spring-boot-starter 4.0.0이 최신이지만 Spring Boot 4.0 전용이며, 3.0.4가 Spring Boot 3.5와 호환되는 최신 안정 버전이다 | 표준 스택 | 3.0.4와 Spring Boot 3.5 간 미발견 비호환 문제 발생 가능성 (낮음) |

---

## 미결 질문

1. **SampleEntity/Mapper 구현 범위**
   - 아는 것: D-04 검증은 "JPA 쓰기 + MyBatis 조회 단일 트랜잭션"을 증명해야 한다
   - 불명확한 것: 검증용 `SampleEntity`와 `SampleMapper`를 `platform` 컨텍스트 안에 두어야 하는가, 아니면 `test` 소스셋에만 두어야 하는가 (D-04: "검증 슬라이스 위치는 planner 재량")
   - 권고사항: `platform` 컨텍스트 안에 최소한의 검증용 엔티티/매퍼를 두어 골격을 실제로 동작하는 상태로 유지하는 것이 "복제해서 사용하는 템플릿" 목적에 부합한다

2. **Redis `@ServiceConnection` 방식 선택**
   - 아는 것: `GenericContainer` + `@ServiceConnection(name="redis")` 또는 `com.redis.testcontainers:RedisContainer` + `@ServiceConnection` 두 방식이 모두 동작한다
   - 불명확한 것: `testcontainers-redis` (com.redis) 라이브러리 추가 없이 `GenericContainer`로 충분한가
   - 권고사항: 추가 의존성 없이 `GenericContainer + @ServiceConnection(name="redis")` 패턴 사용

---

## 출처

### 1차 (HIGH confidence)
- Maven Central 직접 조회 — 모든 패키지 버전 검증
- [Spring Boot 3.5 Release Notes](https://spring.io/blog/2026/04/23/spring-boot-3-5-14-available-now/) — 최신 안정 버전 확인
- [Spring Boot Testcontainers 공식 문서](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — @ServiceConnection 패턴
- [ArchUnit 공식 User Guide](https://www.archunit.org/userguide/html/000_Index.html) — layeredArchitecture(), onionArchitecture() API
- [mybatis.org/spring transactions](https://mybatis.org/spring/transactions.html) — 트랜잭션 관리 원칙
- [mybatis-spring-boot-autoconfigure](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/) — 자동설정 동작
- [Spring Boot Docker Compose 지원](https://spring.io/blog/2023/06/21/docker-compose-support-in-spring-boot-3-1/) — Docker Compose 통합

### 2차 (MEDIUM confidence)
- [thecodinglog.github.io — JPA + MyBatis 단일 트랜잭션](https://thecodinglog.github.io/jpa/mybatis/spring/2019/09/11/jpa-with-mybatis-in-transaction.html) — flush() 필수성 확인
- [Simon Martinelli X 포스트](https://x.com/simas_ch/status/1793965273479082139) — flyway-database-postgresql 분리 확인
- [Spring Boot Improved Testcontainers Support 3.1](https://spring.io/blog/2023/06/23/improved-testcontainers-support-in-spring-boot-3-1/)

### 3차 (LOW confidence / 검증 필요)
- ArchUnit `(*)` 패턴 vs `..` 패턴 동작 — 실행 시 검증 필요 [ASSUMED]

---

## 메타데이터

**신뢰도 분류:**
- 표준 스택: HIGH — Maven Central 직접 버전 검증 완료
- 아키텍처: HIGH — 베이스라인 문서 + 공식 라이브러리 문서 기반
- 함정: HIGH (JPA flush, Flyway PostgreSQL 모듈) / MEDIUM (ArchUnit 패턴 세부)
- 가상 스레드 검증 방법: MEDIUM — 공식 문서에서 `isVirtual()` 확인 방법은 간접 언급

**리서치 날짜:** 2026-05-30
**유효 기간:** 30일 (Spring Boot 3.5.x 패치는 매월 배포되지만 API 변경 없음)
