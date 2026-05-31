# Phase 3: BFF 인증 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-31
**Phase:** 3-bff-auth
**Mode:** Autonomous (non-interactive). 사용자 대화 대신 ROADMAP SC#1~4 · AUTH-01~05 · 정본 §3(A-3/A-4/A-6)·§5.1·§6 NFR-02 · Phase 1/2 코드·ArchUnit 게이트·lesson 교훈에 근거해 결정.
**Areas discussed:** 인증 코드 패키지 위치, OIDC subject→User 매칭 정책, 세션/토큰 저장 메커니즘, 세션 쿠키 구성, 검증 전략(mock OIDC), 빌드 의존성

---

## 인증 코드 패키지 위치 (ArchUnit 게이트 정합)

| Option | Description | Selected |
|--------|-------------|----------|
| 새 `auth` 컨텍스트 (`auth/{infra,application,interfaces}`) | identity와 동형으로 인증을 독립 컨텍스트로 분리. SecurityFilterChain/세션/핸들러는 infrastructure, linkIdentity 브리지는 application | ✓ |
| `common`에 횡단 인프라로 | §5.1 "인증=횡단 인프라" 문구를 글자대로 해석 | |
| identity 컨텍스트 내부에 | 인증이 Identity와 밀접 | |

**선택 근거:** A-4(인증/인가 분리)·§4.1(컨텍스트 경계) — 인증은 식별·인가와 구분되는 독립 책임. ArchUnit 두 점 패턴(`..infrastructure..` 등)이 auth에도 자동 적용되므로 SecurityFilterChain은 infrastructure에 둬야 위반 없음.
**Notes:** `auth.application → identity.application` 호출이 게이트(Application→Domain만 허용)에 걸리는지 = 같은 레이어 간 접근이라 비위반으로 판단하나 **planner가 `./gradlew test --tests *ArchitectureTest*`로 실증 필수**(lesson P1).

---

## OIDC subject → 로컬 User 매칭 정책 (A-6, SC#3)

| Option | Description | Selected |
|--------|-------------|----------|
| 2단계: findByExternalId(재로그인) → 없으면 email로 INVITED 해소 후 linkIdentity | 연결 후엔 불변 oid 매칭, 이메일은 최초 연결에만 (A-6, §5.2) | ✓ |
| 항상 email 매칭 | A-6 위반(이메일 재할당 가능) | |
| 항상 externalId 매칭 | 최초 로그인 시 아직 externalId 미연결이라 불가 | |

**Notes:** 코드 갭 발견 — `UserRepository`에 `findByEmail` 부재(`existsByEmail`만). 최초 로그인 매칭 위해 `findByEmail(Email)` 추가 필요(Identity 내 최소 보강, planner 필수 해소). 미초대 신원 로그인은 baseline 기본 거부(invite-first 모델, JIT는 Deferred).

---

## 세션/토큰 저장 메커니즘 (SC#1, NFR-02)

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Session Data Redis + HttpSessionOAuth2AuthorizedClientRepository | 토큰이 세션→Redis 서버측에만, 쿠키엔 SESSION id만. 프레임워크 기본 동작 | ✓ |
| 토큰을 수동 직렬화해 Redis 키에 별도 저장 | 커스텀 인프라 — NFR-01 위반 | |

**Notes:** Phase 1 Redis 인프라 재사용. 직렬화 포맷(JDK/JSON)은 planner 확정. 리프레시 자동 갱신은 SC 미요구 → Deferred.

---

## 세션 쿠키 구성 (SC#2, BFF 보안)

| Option | Description | Selected |
|--------|-------------|----------|
| HttpOnly + SameSite=Lax + Secure(운영) + CSRF(쿠키 토큰) | 동일 사이트 BFF 전제, JS 토큰 접근 차단, 기본 CSRF 완화 | ✓ |
| SameSite=None | 별 오리진 프론트 — 현재 범위 아님(Deferred) | |

**Notes:** 별 오리진 프론트면 CORS+SameSite=None 재설계 → Deferred.

---

## 검증 전략 (SC#1·#4, 실 브라우저/IdP 없이)

| Option | Description | Selected |
|--------|-------------|----------|
| MockMvc/@SpringBootTest + Testcontainers(Redis), mock OIDC | 로컬 포트 혼잡으로 live run 불가(lesson 02). SC#1=쿠키 단언+Redis 토큰 존재, SC#4=로그아웃 후 401 | ✓ |
| 실 IdP + 실 브라우저 e2e | 환경 차단, 과함 | |

**Notes:** mock OIDC 도구 = spring-security-test `oidcLogin()`(인증 후 상태) vs WireMock/MockWebServer(토큰 교환 흐름까지) 혼합 권장. SC#1/#4 충실도는 후자, SC#2/#3은 전자. planner 확정. lesson 02 P1(VALIDATION 주장↔실제 테스트 존재) 적용.

---

## Claude's Discretion

`auth/infrastructure` 하위 패키지명, SecurityFilterChain 빈·엔드포인트 인가 규칙, 세션 직렬화 포맷, CSRF 세부, 로그아웃 핸들러 방식, mock OIDC 도구 선택, `findByEmail` 파생쿼리 vs `@Query`, 인증 실패 응답 형태(401 JSON vs 리다이렉트), Flyway 마이그레이션 불필요 여부 확인.

## Deferred Ideas

- 리프레시 토큰 자동 갱신 (SC 미요구)
- JIT 자동 사용자 프로비저닝 (invite-first 모델)
- 서버 간 호출 자격증명 (NFR-02 후단, 현재 서버-서버 호출 없음)
- UserActivated 이벤트 구독 (Phase 4)
- CORS/SameSite=None (별 오리진 프론트 배포 형상)
