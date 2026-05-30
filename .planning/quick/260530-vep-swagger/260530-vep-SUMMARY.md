---
quick_id: 260530-vep
slug: swagger
one_liner: springdoc-openapi(Swagger UI + OpenAPI 3)를 추가 스펙으로 도입
date: 2026-05-30
status: complete
key-files:
  created:
    - src/main/java/com/anchors/baseline/common/infrastructure/OpenApiConfig.java
  modified:
    - gradle/libs.versions.toml
    - build.gradle.kts
    - src/main/resources/application.yml
    - src/main/java/com/anchors/baseline/platform/interfaces/rest/SampleController.java
---

# Quick 260530-vep — Swagger(springdoc-openapi) 추가

## 한 일

고정 베이스라인 스택을 변경하지 않고 문서화 의존성과 cross-cutting 설정만 추가.

- **버전 카탈로그**: `libs.versions.toml`에 `springdoc = "2.8.9"` + alias `springdoc-openapi-starter-webmvc-ui`. `build.gradle.kts`는 `implementation(libs.springdoc.openapi.starter.webmvc.ui)`로 선언 (하드코딩 없음).
- **OpenApiConfig**: `common/infrastructure`에 `@Bean OpenAPI`(title "Baseline API"). SecurityScheme 배선은 의도적 제외(Spring Security 미도입, Phase 3/4).
- **application.yml**: `springdoc.api-docs.path=/v3/api-docs`, `swagger-ui.path=/swagger-ui.html`, `api-docs.enabled=true` + 운영 노출 보안 주석(BFF/NFR-02 — 운영 전 비활성화/인가 격리).
- **SampleController**: `@Tag("Sample")` + `create`/`findAll`에 `@Operation` (light 예시, 시그니처/로직 불변).

## 검증

- `./gradlew compileJava` → BUILD SUCCESSFUL
- `./gradlew dependencies --configuration runtimeClasspath` → `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9` 해석
- `./gradlew test` 전체 스위트 → 8 tests, 0 failures (springdoc 추가로 인한 회귀 없음)
- 애노테이션: `@Tag` 1개, `@Operation` 2개 확인
- (옵션/수동) 라이브 `/swagger-ui.html`·`/v3/api-docs` 확인은 DB 기동 필요 — 미수행

## 참고

- 운영 노출 게이트: `springdoc.api-docs.enabled`는 dev에서 true. 운영 배포 전 false 또는 인증 뒤 격리 필요(application.yml 주석에 명시).
- springdoc 패치 상향 시 `[versions] springdoc` 한 줄만 변경(2.8.x 전체가 SB 3.5.x 호환).
