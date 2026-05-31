# 05-02 SUMMARY — AuthController 신설 (session/login)

**Wave:** 2 | **Requirements:** AUTH-06, AUTH-10 | **Status:** ✅ Complete

## 무엇을 했는가

`auth/interfaces/AuthController.java` 신규 생성 (`@RestController @RequestMapping("/api/auth")`):

- **GET /api/auth/session** (AUTH-06) — nullable `@AuthenticationPrincipal OidcUser`. null → `SessionResponse(false, null)`, non-null → `SessionResponse(true, getAttribute("user_id"))`. permitAll 매처라 비인증 200(D-03).
- **GET /api/auth/login** (AUTH-10) — `returnTo` 안전 검증(`isSafeRelativePath`: 상대경로만, `//`·`://` 거부 → open-redirect 방지) 후 세션 `RETURN_TO` 저장. `ObjectProvider<ClientRegistrationRepository>` 가드 — registration 부재 시 503 안전 실패(fail-closed). 그 외 `/oauth2/authorization/test-idp` 302.
- **SessionResponse record** — `boolean authenticated` + `@JsonInclude(NON_NULL) Object userId` (Map.of NPE 회피, lesson 03).

MeController 의 표준 `OidcUser.getAttribute` 패턴 계승 — infrastructure OIDC principal 타입 미참조로 ArchUnit 자연 통과(D-06).

## 검증

- `./gradlew test --tests "*ArchitectureTest*" --tests "*BffAuthIT*"` → **BUILD SUCCESSFUL** (7s). ArchUnit GREEN(interfaces→infrastructure 위반 없음), Wave 1 회귀 없음.
- grep: BaselineOidcUser(0), isSafeRelativePath(3=선언+2호출), ObjectProvider(4), record SessionResponse(1).
- 주석에 있던 literal `BaselineOidcUser` 토큰 제거(acceptance grep==0 충족, 기능 영향 없음).

## 비고

- 신규 외부 의존 없음(NFR-01). registrationId `"test-idp"` 하드코딩(A3 가정 — 단일 registration 전제).
- login 엔드포인트의 실 흐름(returnTo 복원 후 /dashboard 복귀)은 Wave 3 BffAuthSessionIT 에서 WireMock 전체 흐름으로 단언.
