---
phase: quick
plan: 260530-vep-swagger
type: execute
wave: 1
depends_on: []
files_modified:
  - gradle/libs.versions.toml
  - build.gradle.kts
  - src/main/java/com/anchors/baseline/common/infrastructure/OpenApiConfig.java
  - src/main/resources/application.yml
  - src/main/java/com/anchors/baseline/platform/interfaces/rest/SampleController.java
autonomous: true
requirements:
  - SWAGGER-01  # springdoc-openapi 의존성 (버전 카탈로그)
  - SWAGGER-02  # OpenAPI 빈 설정 (common/infrastructure)
  - SWAGGER-03  # application.yml springdoc 설정 + 보안 게이팅
  - SWAGGER-04  # SampleController 예시 애노테이션

must_haves:
  truths:
    - "springdoc-openapi-starter-webmvc-ui 의존성이 버전 카탈로그(libs.*)로 선언되어 해석된다"
    - "OpenAPI 메타데이터(title/version/description) 빈이 common/infrastructure에 존재한다"
    - "application.yml에 springdoc api-docs/swagger-ui 경로와 enabled 게이트가 설정된다"
    - "운영 노출 위험(NFR-02/BFF)이 문서화된 주석으로 명시된다"
    - "compileJava가 BUILD SUCCESSFUL 된다"
  artifacts:
    - path: "gradle/libs.versions.toml"
      provides: "springdoc 버전/라이브러리 alias 핀"
      contains: "springdoc"
    - path: "build.gradle.kts"
      provides: "libs.springdoc 의존성 선언"
      contains: "libs.springdoc"
    - path: "src/main/java/com/anchors/baseline/common/infrastructure/OpenApiConfig.java"
      provides: "OpenAPI 메타데이터 빈"
      contains: "OpenAPI"
    - path: "src/main/resources/application.yml"
      provides: "springdoc 설정 + 게이팅"
      contains: "springdoc"
  key_links:
    - from: "build.gradle.kts"
      to: "gradle/libs.versions.toml"
      via: "libs.springdoc alias"
      pattern: "libs\\.springdoc"
---

<objective>
프로젝트에 springdoc-openapi(Swagger/OpenAPI 문서 + UI)를 **추가 스펙**으로 도입한다.

고정된 베이스라인 스택(Java 21 / Spring Boot 3.5.3 MVC / JPA+MyBatis / PostgreSQL / Redis / Flyway / Lombok)은 일절 변경하지 않고, 문서화 의존성과 cross-cutting 설정만 더한다.

Purpose: 베이스라인을 복제해 쓰는 후속 프로젝트가 REST API 문서/탐색 UI를 곧바로 사용할 수 있도록 검증된 OpenAPI 골격을 제공한다.
Output:
- `libs.versions.toml` / `build.gradle.kts`에 springdoc 의존성 (버전 카탈로그 alias)
- `common/infrastructure/OpenApiConfig.java` (OpenAPI 메타데이터 빈)
- `application.yml`의 springdoc 설정 + 운영 노출 게이팅
- `SampleController`에 최소 OpenAPI 애노테이션 예시
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@CLAUDE.md
@spring-backend-ddd-baseline.md
@gradle/libs.versions.toml
@build.gradle.kts
@src/main/resources/application.yml
@src/main/java/com/anchors/baseline/platform/interfaces/rest/SampleController.java

<interfaces>
<!-- 실행자가 알아야 할 기존 컨트랙트. 추가 탐색 불필요. -->

기존 패키지 레이아웃 (헥사고날 + DDD):
  com.anchors.baseline.{platform,common}.{domain,application,infrastructure,interfaces}
  - cross-cutting 설정 빈은 common/infrastructure 에 위치 (ArchUnit 레이어 게이트 허용)
  - domain/application 에 인프라/문서 설정 금지

버전 카탈로그 컨벤션 (gradle/libs.versions.toml):
  [versions] 에 버전 핀 → [libraries] 에 module + version.ref → build.gradle.kts 에서 libs.<alias>
  외부 의존성 버전을 build.gradle.kts 에 하드코딩 금지

SampleController (src/.../platform/interfaces/rest/SampleController.java):
  @RestController @RequestMapping("/api/v1/samples")
  - POST  /api/v1/samples        create(CreateSampleRequest{value:String}) -> Long, 201
  - GET   /api/v1/samples        findAll() -> List<SampleDto>
  - record CreateSampleRequest(String value)

springdoc 버전 결정 (검증 완료):
  - springdoc 2.8.x 라인은 Spring Boot 3.5.x 를 타깃 (2.8.17 = SB 3.5.13 기준 빌드)
  - 본 플랜은 2.8.9 핀 (SB 3.4/3.5 대상, 3.5.3 BOM 호환 [High])
  - artifact: org.springdoc:springdoc-openapi-starter-webmvc-ui
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: springdoc 의존성 추가 + OpenAPI 빈 + application.yml 설정</name>
  <files>
    gradle/libs.versions.toml,
    build.gradle.kts,
    src/main/java/com/anchors/baseline/common/infrastructure/OpenApiConfig.java,
    src/main/resources/application.yml
  </files>
  <action>
    1. `gradle/libs.versions.toml`:
       - `[versions]` 에 `springdoc = "2.8.9"` 추가 (SWAGGER-01).
       - `[libraries]` 에 alias 추가:
         `springdoc-openapi-starter-webmvc-ui = { module = "org.springdoc:springdoc-openapi-starter-webmvc-ui", version.ref = "springdoc" }`.
       - 주석으로 "springdoc 2.8.x → Spring Boot 3.5.x 호환" 한 줄 명시.

    2. `build.gradle.kts` `dependencies { }` 블록:
       - 기존 starter들 아래에 `implementation(libs.springdoc.openapi.starter.webmvc.ui)` 추가 (SWAGGER-01).
       - 베이스라인 스택 라인은 손대지 않는다 (additive only, 요구사항 2).

    3. `src/main/java/com/anchors/baseline/common/infrastructure/OpenApiConfig.java` 신규 (SWAGGER-02):
       - package `com.anchors.baseline.common.infrastructure`.
       - `@Configuration` 클래스, `@Bean public OpenAPI baselineOpenApi()` 메서드 하나.
       - `io.swagger.v3.oas.models.OpenAPI` + `io.swagger.v3.oas.models.info.Info` 사용.
       - Info: title "Baseline API", version "0.0.1-SNAPSHOT", description "Spring DDD 백엔드 베이스라인 API 문서".
       - 인증 SecurityScheme 등 추가 배선 금지 (Spring Security 미도입, Phase 3/4, 요구사항 5).

    4. `src/main/resources/application.yml` 최상위에 `springdoc` 블록 추가 (SWAGGER-03):
       - `springdoc.api-docs.path: /v3/api-docs`
       - `springdoc.swagger-ui.path: /swagger-ui.html`
       - `springdoc.api-docs.enabled: true` 로 두되, 바로 위에 보안 주석 명시 (요구사항 5, NFR-02/BFF):
         "T-02 보안 주의: Swagger UI / api-docs 는 dev 전용. 운영 배포 전 반드시
          springdoc.api-docs.enabled=false 로 비활성화하거나 인증 뒤로 격리할 것
          (BFF/NFR-02 — 액세스 토큰 브라우저 비노출 원칙). Spring Security 도입(Phase 3/4) 후 인가 연동."
       - 기존 spring/mybatis/management/logging 블록은 변경하지 않는다.
  </action>
  <verify>
    <automated>cd /Users/gyuha/workspace/spring-bootstrap-2026 && ./gradlew compileJava 2>&1 | grep -q 'BUILD SUCCESSFUL'</automated>
    <automated>cd /Users/gyuha/workspace/spring-bootstrap-2026 && ./gradlew dependencies --configuration runtimeClasspath 2>&1 | grep -q 'springdoc-openapi-starter-webmvc-ui'</automated>
  </verify>
  <acceptance_criteria>
    - libs.versions.toml 에 springdoc 버전 핀 + library alias 존재
    - build.gradle.kts 가 libs.* alias 로 의존성 선언 (하드코딩 문자열 아님)
    - OpenApiConfig 가 common/infrastructure 패키지에 존재하고 OpenAPI 빈 1개 노출
    - application.yml 에 springdoc api-docs/swagger-ui 경로 + enabled + 운영 보안 주석 존재
    - ./gradlew compileJava BUILD SUCCESSFUL
    - ./gradlew dependencies 출력에 springdoc-openapi-starter-webmvc-ui 표시
  </acceptance_criteria>
  <done>
    springdoc 의존성이 버전 카탈로그로 해석되고, OpenAPI 메타데이터 빈과 application.yml 설정이 추가되며,
    운영 노출 게이팅이 주석으로 문서화되고 compileJava 가 성공한다.
  </done>
</task>

<task type="auto">
  <name>Task 2: SampleController 최소 OpenAPI 애노테이션 예시</name>
  <files>
    src/main/java/com/anchors/baseline/platform/interfaces/rest/SampleController.java
  </files>
  <action>
    SampleController 에 베이스라인 예시 수준의 가벼운 OpenAPI 애노테이션만 추가한다 (SWAGGER-04, 요구사항 6 — exhaustive 금지):
    - 클래스에 `@Tag(name = "Sample", description = "platform 샘플 API")`
      (`io.swagger.v3.oas.annotations.tags.Tag`).
    - `create` 메서드에 `@Operation(summary = "샘플 생성")`,
      `findAll` 메서드에 `@Operation(summary = "샘플 전체 조회")`
      (`io.swagger.v3.oas.annotations.Operation`).
    - DTO 필드별 @Schema 등 상세 애노테이션은 추가하지 않는다 (light 유지).
    - 기존 매핑/시그니처/비즈니스 로직은 변경하지 않는다 (surgical).
  </action>
  <verify>
    <automated>cd /Users/gyuha/workspace/spring-bootstrap-2026 && ./gradlew compileJava 2>&1 | grep -q 'BUILD SUCCESSFUL'</automated>
    <automated>cd /Users/gyuha/workspace/spring-bootstrap-2026 && grep -q '@Tag' src/main/java/com/anchors/baseline/platform/interfaces/rest/SampleController.java && grep -c '@Operation' src/main/java/com/anchors/baseline/platform/interfaces/rest/SampleController.java | grep -q '2'</automated>
  </verify>
  <acceptance_criteria>
    - SampleController 클래스에 @Tag 1개
    - create/findAll 두 메서드에 각각 @Operation
    - 기존 @RequestMapping / 메서드 시그니처 / 로직 불변
    - ./gradlew compileJava BUILD SUCCESSFUL
  </acceptance_criteria>
  <done>
    SampleController 가 최소 OpenAPI 애노테이션 예시를 갖추고 컴파일된다.
  </done>
</task>

</tasks>

<verification>
- `./gradlew compileJava` → BUILD SUCCESSFUL
- `./gradlew dependencies --configuration runtimeClasspath` → springdoc-openapi-starter-webmvc-ui 표시
- (선택/수동) 부팅 후 라이브 확인 — PostgreSQL/Redis 필요하므로 옵션:
  `./gradlew bootRun` 후 브라우저에서 `http://localhost:8080/swagger-ui.html` 와
  `http://localhost:8080/v3/api-docs` 접근 → Sample 태그/엔드포인트 노출 확인.
</verification>

<success_criteria>
- springdoc 의존성이 버전 카탈로그(libs.*)로만 선언되고 해석된다 (하드코딩 없음).
- 고정 베이스라인 스택은 한 줄도 변경되지 않았다 (additive only).
- OpenApiConfig 빈이 common/infrastructure 에 존재 (ArchUnit 레이어 게이트 준수).
- application.yml 에 springdoc 경로 설정 + 운영 노출 보안 게이팅 주석(NFR-02/BFF)이 명시된다.
- SampleController 에 최소 OpenAPI 애노테이션 예시가 있다.
- `./gradlew compileJava` 가 BUILD SUCCESSFUL 이다.
</success_criteria>

<output>
완료 시 `.planning/quick/260530-vep-swagger/260530-vep-SUMMARY.md` 생성.
</output>
