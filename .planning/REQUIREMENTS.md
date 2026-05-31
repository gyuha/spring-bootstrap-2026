# 요구사항: Spring DDD 백엔드 베이스라인 — v1.1 인증 웹 인터페이스

## 개요

v1.0은 식별/인증/인가 컨텍스트를 도메인·application·영속까지 구현했으나, BFF SPA가 직접 소비할 HTTP 표면은 `GET /api/me`(userId만) 하나뿐이었다. v1.1은 BFF SPA가 인증 흐름을 운용하는 데 필요한 최소 웹 인터페이스를 auth 컨텍스트에 추가한다 — 세션 상태 확인, 로그아웃, 내 정보(신원+권한 집계), 로그인 진입.

**버전:** v1.1 (v1.0 → minor, additive HTTP 표면)
**카테고리:** Auth (v1.0 AUTH-01~05 이어서 AUTH-06부터)

## v1.1 Requirements

### Auth (BFF 인증 웹 인터페이스)

- [ ] **AUTH-06**: 사용자는 `GET /api/auth/session` 으로 자신의 인증 여부를 확인한다. 비인증 상태에서도 401이 아니라 `200 + {authenticated: false}` 를 반환해 SPA가 401 노이즈 없이 로그인 여부를 판별할 수 있다. 인증 시 `{authenticated: true, userId}` 를 반환한다.
- [ ] **AUTH-07**: 사용자는 `POST /api/auth/logout` 으로 로그아웃한다. 프레임워크 기본 302 redirect 대신 `204 No Content` (JSON 흐름)를 반환하고, HttpSession 무효화 + `SESSION` 쿠키 삭제 + 인증 클리어를 수행한다.
- [ ] **AUTH-08**: 사용자는 `GET /api/auth/me` 로 자신의 신원 정보(userId, email, status)를 조회한다. Identity 컨텍스트의 application 서비스에서 로컬 User를 조회해 집계한다. 비인증 시 401.
- [ ] **AUTH-09**: `GET /api/auth/me` 응답에 사용자의 권한 전체(전역 역할 / 메뉴 권한 / 리소스 권한)가 포함된다. Authorization 컨텍스트의 application 서비스에서 권한을 조회해 집계한다.
- [ ] **AUTH-10**: 사용자는 `GET /api/auth/login` 으로 OIDC 로그인 흐름에 진입한다. 프레임워크의 `/oauth2/authorization/{registration}` 진입점을 감싸는 BFF 래퍼로, 로그인 후 복귀 경로(returnTo) 처리를 포함한다.
- [ ] **AUTH-11**: `auth/interfaces`(및 필요한 경우 `auth/application`)가 Identity·Authorization 컨텍스트의 application 서비스를 호출할 수 있도록 계층 의존 규칙을 정비한다. ArchUnit 게이트를 갱신하고 컨텍스트 간 허용 의존이 명시적으로 통과(green)함을 보장한다. — AUTH-08/09 집계를 가능케 하는 구조적 enabler.

## Future Requirements

<!-- 다음 마일스톤 후보 — 이번 범위 아님 -->

- 인가(Authorization) REST API — 권한 부여/회수/조회 엔드포인트. 베이스라인은 의도적으로 미노출(복제 업무 프로젝트가 채움). v1.1은 "내 권한 읽기"만 포함하고 권한 변경 API는 제외.
- 세션 갱신/만료 정책 엔드포인트 — 명시적 토큰 리프레시 트리거 등. v1.1은 프레임워크 기본 세션 수명에 의존.

## Out of Scope

<!-- 명시적 경계 — 재추가 방지 사유 포함 -->

- **프론트엔드/SPA 코드** — 베이스라인은 백엔드 한정(PROJECT.md). v1.1은 SPA가 소비할 REST만 제공하고 SPA 자체는 만들지 않는다.
- **인가 권한 변경 API** — 권한 부여/회수는 복제 업무 프로젝트 영역(PROJECT.md Out of Scope "인가 HTTP API 미포함"). v1.1은 읽기 집계(AUTH-09)만 노출.
- **새 바운디드 컨텍스트** — v1.1은 기존 auth 컨텍스트의 interfaces 계층 확장. 새 컨텍스트 추가 없음.
- **OIDC registration 운영 설정** — 실제 IdP registration 값은 배포 프로파일 영역. v1.1은 main 프로파일 부팅 보전(registration 부재 시 가드)을 유지하며, 검증은 기존 test/WireMock 인프라 사용.

## Traceability

<!-- 로드맵 단계에서 채워짐 -->

| REQ-ID | Phase | Status |
|--------|-------|--------|
| AUTH-06 | — | Not started |
| AUTH-07 | — | Not started |
| AUTH-08 | — | Not started |
| AUTH-09 | — | Not started |
| AUTH-10 | — | Not started |
| AUTH-11 | — | Not started |

---
*작성: 2026-05-31 — v1.1 인증 웹 인터페이스 마일스톤*
