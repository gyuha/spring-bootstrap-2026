# Phase 5: BFF 인증 세션 엔드포인트 - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.
>
> **수집 방식:** 비대화형(autonomous subagent) 세션. 사용자 권한 위임("선택은 알아서 해 줘")에 따라
> 각 그레이 영역을 AskUserQuestion 없이 자율 결정. 아래는 고려된 대안과 선택을 보존한다.

**Date:** 2026-05-31
**Phase:** 5-bff-auth-session
**Areas discussed:** 컨트롤러 배치, 로그아웃 구현, 세션 응답 형태, 로그인 래퍼/returnTo, permitAll 매처, ArchUnit, 검증 전략

---

## 컨트롤러 배치 (Controller placement)

| Option | Description | Selected |
|--------|-------------|----------|
| 신규 AuthController 신설 | `auth/interfaces/AuthController`에 `/api/auth/*` 3종 | ✓ |
| 기존 MeController 확장 | `/api/me` 컨트롤러에 메서드 추가 | |

**선택:** 신규 AuthController.
**Notes:** `/api/auth/*` 경로 prefix 응집, Phase 6 `/api/auth/me` 자연 합류. MeController는 Phase 3 SC#2 검증용 레거시 프로브라 책임 분리. (CONTEXT D-01)

---

## 로그아웃 구현 (Logout endpoint)

| Option | Description | Selected |
|--------|-------------|----------|
| 기존 logout DSL 재사용 + URL 재지정 + 204 핸들러 | `logoutUrl`→`/api/auth/logout`, LogoutSuccessHandler로 204 | ✓ |
| 컨트롤러에서 세션 무효화 직접 구현 | `@PostMapping`에서 HttpSession.invalidate + 쿠키 삭제 손수 | |
| 기존 `/logout` 별도 유지 + 신규 추가(이중) | 두 엔드포인트 공존 | |

**선택:** 기존 logout 머신 재사용 + LogoutSuccessHandler 204 전환, 단일 `/api/auth/logout`로 통일.
**Notes:** 검증된 프레임워크 세션 무효화(invalidate+clearAuth+deleteCookies SESSION) 재사용 — 재발명 금지(simplicity-first). Phase 3 `logoutInvalidatesSession`가 행위 증명. logoutUrl 변경이 기존 테스트 헬퍼를 깨므로 동반 갱신 필요(회귀 면 — planner 태스크화). (CONTEXT D-02)

---

## 세션 응답 형태 (Session response shape)

| Option | Description | Selected |
|--------|-------------|----------|
| `{authenticated:bool}` + 인증 시 userId | 비인증 200 `{authenticated:false}`, 인증 200 `{authenticated:true,userId}` | ✓ |
| 항상 동일 키(userId:null) | 비인증도 userId 키 포함(null) | |

**선택:** authenticated boolean + 인증 시에만 userId 키.
**Notes:** AUTH-06 명세 직접 반영. userId는 `OidcUser.getAttribute("user_id")`(BaselineOidcUser 병합 로컬 PK). Map.of null NPE 회피(record/LinkedHashMap). (CONTEXT D-03)

---

## 로그인 래퍼 / returnTo (Login wrapper)

| Option | Description | Selected |
|--------|-------------|----------|
| 얇은 302 래퍼 + returnTo 상대경로 검증 | `/oauth2/authorization/{registration}`로 위임, returnTo open-redirect 방지 | ✓ |
| returnTo 검증 없이 그대로 전달 | 절대 URL 허용 | |

**선택:** 얇은 진입 래퍼 + returnTo는 같은 사이트 상대경로만 허용(open-redirect 거부).
**Notes:** 미검증 returnTo는 open-redirect 취약점. 동일 사이트 BFF 전제(Phase 3 D-06). returnTo 보존은 SavedRequest/요청 캐시 또는 명시 파라미터 — Phase 3 OIDC 성공 핸들러 구조와 정합(planner 확정). (CONTEXT D-04)

---

## permitAll 매처 조정 (SecurityConfig)

| Option | Description | Selected |
|--------|-------------|----------|
| session·login만 추가, logout 제외, `/api/**` 불변 | 구체 매처 2개를 광역 401 엔트리포인트보다 선행 | ✓ |
| session·login·logout 모두 추가 | logout도 permitAll | |

**선택:** `/api/auth/session`·`/api/auth/login`만 permitAll 추가.
**Notes:** login은 미인증 진입(필수), session은 미인증 200. logout은 CSRF만 요구하므로 permitAll 불필요. `/api/**` 401 엔트리포인트 불변 — surgical. 매처 순서가 SC#4 결정적 동작(구체 먼저). (CONTEXT D-05)

---

## ArchUnit

| Option | Description | Selected |
|--------|-------------|----------|
| Phase 5 무변경 | 신규 코드가 기존 게이트 자연 통과 | ✓ |
| 교차 컨텍스트 의존 규칙 선반영 | AUTH-11을 Phase 5에서 미리 | |

**선택:** ArchUnit 무변경.
**Notes:** Phase 5는 Identity/Authorization application 교차 의존 없음 — AUTH-11은 그 집계가 필요한 Phase 6 enabler. 변경 시 범위 침범 + lesson 01 P1(PLAN↔게이트 자기모순) 위험. (CONTEXT D-06)

---

## 검증 전략 (Verification)

| Option | Description | Selected |
|--------|-------------|----------|
| Testcontainers + WireMock + MockMvc | BffAuthIT 형판 재사용·확장, 라이브 구동 없음 | ✓ |
| 라이브 앱 구동 | 실 포트/IdP 기동 | |

**선택:** Testcontainers(postgres:16/redis:7) + WireMock(MockOidcServer) + MockMvc/RestTemplate.
**Notes:** 포트 혼잡(8080/5432/6379)으로 live run 차단(lesson). 세션 라운드트립 단언(logout)은 WireMock 실 토큰 교환, 상태만 필요한 단언은 oidcLogin() — Phase 3 혼합 전략 계승. (CONTEXT D-07)

---

## Claude's Discretion

비대화형 세션이라 모든 영역을 자율 결정했다. 추가로 planner/researcher 재량으로 명시 위임한 세부:
- 응답 DTO 형태(record vs Map vs LinkedHashMap), AuthController 메서드 시그니처
- returnTo 검증 구현(정규식 vs allowlist), 로그인 래퍼 registration 파라미터 방식
- LogoutSuccessHandler 구현(HttpStatusReturningLogoutSuccessHandler vs 커스텀)
- permitAll 매처 표현, 신규 IT 클래스 분리 vs BffAuthIT 확장

모든 자율 결정은 CONTEXT.md "Open Questions / Decisions" 표에 근거·신뢰도와 함께 기록됨.

## Deferred Ideas

- `GET /api/auth/me` 신원·권한 집계 (AUTH-08/09) — Phase 6
- ArchUnit 계층 의존 규칙 정비 (AUTH-11) — Phase 6
- `/api/me`(MeController) deprecate/정리 — Phase 6
- 다중 registration 선택 UI / IdP 디스커버리 — 복제 업무 프로젝트
- 리프레시 토큰 자동 갱신, JIT 프로비저닝, 서버 간 자격증명, CORS(별 오리진) — v1.1 범위 밖(Phase 3 Deferred 계승)
