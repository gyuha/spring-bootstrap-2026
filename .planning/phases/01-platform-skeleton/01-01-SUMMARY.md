# 01-01 SUMMARY — Gradle 프로젝트 베이스 + 의존성 버전 고정

**실행일:** 2026-05-30
**계획:** 01-01-PLAN.md
**요구사항:** PLAT-01, PLAT-04
**상태:** 완료 (모든 verify BUILD SUCCESSFUL)

---

## 무엇을 만들었나

Gradle (Kotlin DSL) 단일 모듈 프로젝트 베이스를 세우고, 모든 외부 의존성 버전을 버전 카탈로그에 고정했다. Flyway PostgreSQL 분리 모듈 누락 함정과 MyBatis 버전 오선택 함정을 의존성 선언 단계에서 차단했다.

### 생성 파일

| 파일 | 역할 |
|------|------|
| `gradle/libs.versions.toml` | 버전 카탈로그. spring-boot=3.5.3, mybatis-spring-boot=3.0.4, archunit=1.4.1, testcontainers=1.21.3, lombok=1.18.38 고정 |
| `settings.gradle.kts` | rootProject.name="baseline", TYPESAFE_PROJECT_ACCESSORS 미리보기 활성화 |
| `build.gradle.kts` | Spring Boot BOM + 전체 의존성 선언, Java 21 toolchain, JUnit Platform |
| `src/main/java/com/anchors/baseline/BaselineApplication.java` | @SpringBootApplication + 가상 스레드 검증 CommandLineRunner |
| `gradlew`, `gradle/wrapper/*` | Gradle 8.14.3 래퍼 (부트스트랩) |

### 핵심 의존성 (build.gradle.kts)

- implementation: spring-boot-starter-web / data-jpa / data-redis / actuator
- implementation: `libs.mybatis.spring.boot.starter` (3.0.4, 카탈로그 관리)
- implementation: `org.flywaydb:flyway-core` + **`org.flywaydb:flyway-database-postgresql`** (누락 시 기동 실패)
- compileOnly + annotationProcessor: lombok
- runtimeOnly: postgresql
- test: spring-boot-starter-test, spring-boot-testcontainers, testcontainers postgresql/junit-jupiter, archunit-junit5, lombok

---

## 검증 증거

### 환경 부트스트랩 (계획 외 선행 작업)

프로젝트에 Gradle 래퍼가 없었다. 시스템 Gradle 9.5.1은 Spring Boot 3.5.3 플러그인 공식 미지원이므로,
호환 버전으로 래퍼를 고정 생성했다.

```
gradle wrapper --gradle-version 8.14.3
→ BUILD SUCCESSFUL
distributionUrl=.../gradle-8.14.3-bin.zip   (확인됨)
```

### TASK 1 — `./gradlew dependencies --configuration compileClasspath`

```
+--- org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4
|    +--- org.mybatis.spring.boot:mybatis-spring-boot-autoconfigure:3.0.4
|    +--- org.mybatis:mybatis:3.5.17
|    \--- org.mybatis:mybatis-spring:3.0.4
BUILD SUCCESSFUL in 515ms
```

수락 기준 충족:
- [x] build.gradle.kts에 "flyway-database-postgresql" 포함
- [x] libs.versions.toml: mybatis-spring-boot="3.0.4", spring-boot="3.5.3"
- [x] BUILD SUCCESSFUL
- [x] 출력에 "mybatis-spring-boot-starter:3.0.4"
- [x] mybatis 의존성에 "4.0.0" 없음

### TASK 2 — `./gradlew compileJava`

```
> Task :compileJava
BUILD SUCCESSFUL in 3s
```

수락 기준 충족:
- [x] 파일 위치: src/main/java/com/anchors/baseline/BaselineApplication.java
- [x] 첫 줄 "package com.anchors.baseline;"
- [x] @SpringBootApplication 존재
- [x] @Slf4j + CommandLineRunner verifyVirtualThreads 존재
- [x] BUILD SUCCESSFUL

---

## 계획과의 차이

1. **Gradle 래퍼 부트스트랩 (선행):** 계획 verify가 `./gradlew`를 호출하지만 래퍼가 없었다.
   Spring Boot 3.5.3 미지원인 시스템 Gradle 9.5.1 대신 8.14.3으로 래퍼를 고정 생성했다.
   계획 본문에는 없으나 verify 실행을 위해 필수.

2. **그 외 차이 없음.** TOML/build.gradle.kts 내용은 01-RESEARCH §Pattern 8과 동일.
   `mavenCentral()` 리포지토리와 `enableFeaturePreview` 는 RESEARCH/PLAN 명시 항목.

application.yml은 본 계획 범위 밖(후속 계획 소관)이므로 생성하지 않았다.
