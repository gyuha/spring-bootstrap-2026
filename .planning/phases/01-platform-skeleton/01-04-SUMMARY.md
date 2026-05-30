# 01-04 실행 요약 — platform/common 골격 계층

**실행일:** 2026-05-30
**범위:** platform 바운디드 컨텍스트의 헥사고날+DDD 계층 골격 + common 패키지 형판 + D-04용 SampleEntity/SampleMapper 동작 구현

## 생성/수정 파일

### main 소스 (생성, 12개)
- `platform/domain/model/SampleEntity.java` — `@Entity @Table(name="platform_sample")`, `@GeneratedValue(IDENTITY)`, `protected` 무인자 생성자(JPA) + `public SampleEntity(String)`, JPA 방식 A.
- `platform/domain/repository/SampleRepository.java` — 도메인 포트 `interface SampleRepository { SampleEntity save(...); }`.
- `platform/infrastructure/jpa/SampleJpaRepository.java` — `interface ... extends JpaRepository<SampleEntity, Long>, SampleRepository` (포트 어댑터).
- `platform/application/SampleQuery.java` — **읽기 포트(신규)**. application 이 정의.
- `platform/application/SampleDto.java` — 읽기 모델 DTO. `@Data @NoArgsConstructor @AllArgsConstructor` (record 아님). **infrastructure 가 아니라 application 에 배치(아래 편차 2).**
- `platform/infrastructure/mybatis/SampleMapper.java` — `@Mapper interface SampleMapper extends SampleQuery`, `@Select(...) findAll()` (SampleQuery 포트의 MyBatis 어댑터).
- `platform/application/SampleApplicationService.java` — `@Service @RequiredArgsConstructor @Transactional`. `SampleRepository`(도메인 포트) + `SampleQuery`(읽기 포트) 주입. SampleJpaRepository/SampleMapper 직접 주입 안 함.
- `platform/interfaces/rest/SampleController.java` — `@RestController`, `@RequestMapping("/api/v1/samples")`, POST→201, GET→200. application 만 의존.
- `common/{domain,application,infrastructure,interfaces}/package-info.java` — 4개 패키지 형판(Javadoc + package 선언).

### test 소스 (수정, 1개)
- `architecture/ArchitectureTest.java` — 편차 3 참조.

## 검증 결과

- `./gradlew compileJava` → **BUILD SUCCESSFUL**.
- `./gradlew test --tests "*ArchitectureTest*"` → **BUILD SUCCESSFUL (GREEN)**. ArchUnit 은 순수 정적 분석으로 Docker/Spring 컨텍스트 불필요, 1초 내 통과. `interfaces → application → domain`, `infrastructure → {domain, application}` 의존 방향이 실제 계층 코드 위에서 강제됨을 확인.

## 편차 (Deviations)

### 편차 1 — SampleJpaRepository: "implements" vs "extends"
계획 수용 기준 텍스트는 `"implements SampleRepository"` grep 을 명시했으나, JPA 리포지토리는 **인터페이스**이므로 `extends JpaRepository<...>, SampleRepository` 형태가 정확하다. 계획 `<interfaces>` 절도 extends 형을 지정. 가짜로 "implements" 를 넣지 않고 `extends ... , SampleRepository`(포트)로 구현. grep "implements SampleRepository" 는 인터페이스에서 문자 그대로 성립할 수 없음.

### 편차 2 — SampleDto/SampleQuery 를 application 으로 이동 (ArchUnit 충돌 해소)
계획은 SampleDto/SampleMapper 를 `infrastructure.mybatis` 에 두고 ApplicationService 가 SampleMapper 를, Controller 가 SampleDto 를 쓰도록 명시했다. 그러나 **이미 커밋된(D-05) ArchUnit 게이트**는 `Application mayOnlyAccessLayers(Domain)`, `Interfaces mayOnlyAccessLayers(Application, Domain)` 로 application→infrastructure, interfaces→infrastructure 접근을 금지한다. 두 결정이 상호 모순.

헥사고날/베이스라인 §4.5(`Controller → QueryService → Mapper → DTO`)에 맞춰 해소:
- `SampleDto`(읽기 모델 결과 타입)와 `SampleQuery`(읽기 포트)를 **application** 에 배치.
- `SampleMapper`(@Mapper, infrastructure)가 `SampleQuery` 를 구현하는 **어댑터**로 둠. infrastructure→application 은 게이트 허용.
- ApplicationService 는 `SampleQuery` 포트만, Controller 는 application 의 `SampleDto` 만 의존.

결과적으로 `SampleMapper`/`@Select`/`findAll` 은 그대로 infrastructure.mybatis 에 존재(수용 기준 충족), DTO 위치만 application 으로 조정. ApplicationService 가 SampleJpaRepository 를 주입하지 않는다는 핵심 ArchUnit 요구도 충족.

### 편차 3 — ArchitectureTest 수정 (Wave 1+2 스캐폴드 파일)
ArchitectureTest 는 **내 코드와 무관하게 이미 red 였음**(main 계층 코드를 모두 제거하고 실행해도 동일 실패 — git 미추적 스캐폴드). 원인 두 가지:
1. `importPackages(ROOT)` 가 **테스트 클래스까지** 스캔 — `...platform.infrastructure.*` 패키지의 통합 테스트들이 Infrastructure 레이어로 잡혀 분석 오염.
2. `.consideringAllDependencies()` 가 `java.lang.Object`/Spring/jakarta 등 **어느 레이어에도 속하지 않는** 클래스 의존까지 위반으로 카운트(168건).

수정(계층 게이트가 실제로 의미 있게 동작하도록 최소 교정):
- `.withImportOption(new ImportOption.DoNotIncludeTests())` — 테스트 클래스 제외.
- `.consideringAllDependencies()` → `.consideringOnlyDependenciesInLayers()` — 정의된 4계층 간 의존만 검사(ArchUnit 표준 관용구).

레이어 규칙(whereLayer ...) 자체는 변경하지 않음. 의존 방향 강제 의도(D-05/PLAT-02)는 그대로 유지·강화됨.

## 미해결/후속

- `PersistenceIntegrationTest`(D-04)는 본체가 여전히 `@Disabled` 상태(SampleEntity/SampleMapper 참조가 주석). 계획 01-05 에서 주석 해제 시, `SampleDto` import 경로가 `platform.application.SampleDto`, MyBatis 조회는 `SampleQuery`/`SampleMapper.findAll()` 임에 유의(편차 2로 패키지 이동됨).
