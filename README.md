# spring-bootstrap-2026

Spring Boot 3 기반의 프로덕션 레디 백엔드 부트스트랩 프로젝트입니다.  
새 프로젝트 시작 시 이 저장소를 복제하여 반복 설정 없이 바로 개발에 집중할 수 있도록 설계되었습니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| 언어 / 런타임 | Java 21, Spring Boot 3.4.5 |
| 웹 프레임워크 | Spring WebFlux (Reactive) |
| 보안 | Spring Security, JWT (Access 30분 / Refresh 14일), OAuth2 (Google, Kakao) |
| DB (Reactive) | R2DBC + PostgreSQL 16 |
| DB (Migration) | Flyway (JDBC 기반) |
| 캐시 / 세션 | Redis 7 (JWT 블랙리스트, 캐싱) |
| 배치 | Spring Batch (만료 토큰 정리) |
| AI | Spring AI 1.0.0 + OpenAI (gpt-4o-mini) |
| 빌드 | Gradle 8, Checkstyle, SpotBugs, JaCoCo (60% 커버리지 기준) |
| 코드 생성 | Lombok, MapStruct |
| API 문서 | SpringDoc OpenAPI / Swagger UI |
| 관측성 | Micrometer, Prometheus, Grafana |
| 테스트 | JUnit 5, Testcontainers, Reactor Test |

## 프로젝트 구조

```
src/main/java/com/example/bootstrap/
├── account/                  # 사용자 계정 도메인 (인증, OAuth2)
│   ├── application/          # DTO, Service
│   ├── domain/               # Entity, Repository 인터페이스
│   └── infrastructure/       # OAuth2 핸들러 (Google, Kakao)
├── ai/                       # AI 채팅 도메인
│   ├── application/          # Service
│   └── controller/           # REST 컨트롤러
├── batch/                    # Spring Batch 도메인
│   ├── application/          # Job 설정, Service
│   └── controller/           # 배치 트리거 컨트롤러
└── global/
    ├── config/               # Spring 설정 클래스
    ├── exception/            # 공통 예외 처리
    ├── response/             # API 응답 래퍼 (ApiResponse, PageResponse)
    ├── cache/                # Redis 캐시 유틸
    └── security/jwt/         # JWT 토큰 발급 / 검증 / 블랙리스트
```

## 도메인 구조 (DB 스키마)

```
users ─────────────┬──── refresh_tokens
                   └──── oauth_accounts

ai_chat_sessions ──────── ai_chat_messages
```

Spring Batch 메타데이터 테이블은 `V2__batch_schema.sql`로 별도 관리됩니다.

## API 엔드포인트

| 메서드 | 경로 | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/v1/auth/**` | 불필요 | 회원가입, 로그인, 소셜 인증 |
| POST | `/api/v1/ai/chat` | 필요 | AI 동기 채팅 |
| GET | `/api/v1/ai/chat/stream` | 필요 | AI SSE 스트리밍 채팅 |
| POST | `/api/v1/admin/batch/expired-tokens` | ADMIN | 만료 Refresh Token 정리 |
| GET | `/actuator/health`, `/actuator/prometheus` | 불필요 | 헬스 체크, 메트릭 |
| GET | `/swagger-ui.html` | 불필요 (local) | API 문서 |

## 빠른 시작

### 사전 조건

- Java 21
- Docker & Docker Compose
- [Task](https://taskfile.dev) (선택 — 없으면 `./gradlew` 직접 사용)

### 로컬 실행

```bash
# 1. 저장소 복제
git clone <repo-url>
cd spring-bootstrap-2026

# 2. 환경변수 파일 복사 및 편집
cp .env.example .env

# 3. 인프라(PostgreSQL, Redis) 실행 + 앱 시작
task run
```

`task run`은 PostgreSQL·Redis 컨테이너가 healthy 상태가 된 후 `local` 프로파일로 앱을 실행합니다.

### 로컬 개발 접속 정보

| 서비스 | URL | 계정 |
|--------|-----|------|
| 애플리케이션 | http://localhost:8080 | `user` / `user` |
| Swagger UI | http://localhost:8080/swagger-ui.html | 인증 불필요 |
| Health | http://localhost:8080/actuator/health | 인증 불필요 |
| Prometheus | http://localhost:9090 | 인증 불필요 |
| Grafana | http://localhost:3000 | `admin` / `admin` |

> `user` / `user` 계정은 `local` 프로파일에서만 활성화됩니다. (`SecurityConfig` → `@Profile("local")` Bean)

### 전체 스택 Docker 실행

```bash
# 이미지 빌드 + 전체 스택 실행
task docker:up

# 로그 확인
task docker:logs

# 종료
task docker:down
```

## 환경변수

`.env.example`을 복사하여 `.env`로 만든 뒤 아래 항목을 수정합니다.

```bash
cp .env.example .env
```

| 변수 | 기본값 | 설명 |
|------|--------|------|
| `POSTGRES_DB` | `bootstrap` | DB 이름 |
| `POSTGRES_USER` | `bootstrap` | DB 사용자 |
| `POSTGRES_PASSWORD` | `bootstrap` | DB 비밀번호 |
| `JWT_SECRET` | `bootstrap-secret-...` | **운영 시 반드시 교체** (32자 이상) |
| `OPENAI_API_KEY` | `placeholder-key` | OpenAI API 키 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,...` | CORS 허용 Origin 목록 |
| `GF_ADMIN_USER` | `admin` | Grafana 관리자 ID |
| `GF_ADMIN_PASSWORD` | `admin` | Grafana 관리자 비밀번호 |

## 사용 가능한 Task 명령

```bash
task build              # Gradle 빌드
task test               # JUnit 테스트
task coverage           # 테스트 + JaCoCo HTML 리포트
task lint               # Checkstyle + SpotBugs 정적 분석
task check              # 테스트 + 커버리지 검증 + 정적 분석 (CI 기준)
task ci                 # clean + check (CI 파이프라인용)

task run                # 인프라 실행 후 local 프로파일로 앱 시작
task docker:up          # 전체 스택 Docker 실행
task docker:down        # 전체 스택 중지
task docker:logs        # 로그 follow

task db:shell           # psql 접속
task redis:shell        # redis-cli 접속
task health:check       # Actuator health 확인
task reports:open       # HTML 리포트 열기 (macOS)
```

전체 목록: `task help`

## 프로파일

| 프로파일 | 용도 |
|----------|------|
| `local` | 로컬 개발 — Swagger UI 활성화, 상세 로그, `user`/`user` 계정 활성화 |
| `prod` | 운영 — Swagger 비활성화, JSON 로그 (Logstash) |

## 서비스 포트

| 서비스 | 기본 포트 |
|--------|-----------|
| 애플리케이션 | 8080 |
| PostgreSQL | 5432 (호스트: 15432) |
| Redis | 6379 (호스트: 16379) |
| Prometheus | 9090 |
| Grafana | 3000 |

## 새 프로젝트에 적용하기

`gradle.properties`에서 아래 항목을 변경합니다.

```properties
projectGroup=com.yourcompany
projectVersion=0.1.0-SNAPSHOT
basePackage=com.yourcompany.yourapp
```

패키지 이름(`com.example.bootstrap`)도 IDE의 리팩터링 기능으로 일괄 변경합니다.

## 코드 품질 기준

- 라인 커버리지 60% 미만 시 빌드 실패 (`./gradlew check`)
- Checkstyle 경고 0 허용
- SpotBugs `medium` 이상 이슈 0 허용
- 생성 코드(MapStruct, Spring Boot entry-point, Config 클래스)는 커버리지 제외
