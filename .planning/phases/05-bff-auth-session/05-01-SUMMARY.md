# 05-01 SUMMARY — SecurityConfig 로그아웃/permitAll + BffAuthIT 회귀 수정

**Wave:** 1 | **Requirements:** AUTH-07 | **Status:** ✅ Complete

## 무엇을 했는가

`SecurityConfig.java` 외과적 수정 3곳:
1. **logout DSL** — `logoutUrl("/logout")` → `"/api/auth/logout"`, `deleteCookies("SESSION")` 뒤에 `logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))` 추가 (302 → 204).
2. **permitAll 매처** — 기존 목록 끝에 `"/api/auth/session"`, `"/api/auth/login"` 추가. `/api/auth/logout`은 미추가(D-05), 기존 `/api/**` 401 엔트리포인트 불변.
3. **oauth2Login successHandler** — 세션 `RETURN_TO` attribute를 복원해 `sendRedirect`, 없으면 `/` (D-04). open-redirect 검증은 진입점(AuthController.login)에서 수행.

import 2개 추가: `HttpStatusReturningLogoutSuccessHandler`, `jakarta.servlet.http.HttpSession`.

`BffAuthIT.java` 292행 회귀 수정: `logout()` 헬퍼의 `/logout` → `/api/auth/logout` (logoutUrl 변경 동반).

## 검증

- `./gradlew test --tests "*BffAuthIT*" --tests "*ArchitectureTest*"` → **BUILD SUCCESSFUL** (10s). 기존 AUTH-01~05 회귀 없음, ArchUnit GREEN(D-06 무변경).
- grep 단언: `/api/auth/logout`(1), `HttpStatusReturningLogoutSuccessHandler`(2), `api/auth/session`(1), BffAuthIT `/logout` 하드코딩 잔존(0) 모두 통과.

## 비고

- 신규 외부 의존 없음(NFR-01). 기존 spring-security-web jar의 `HttpStatusReturningLogoutSuccessHandler` 사용.
- Wave 2(AuthController)·Wave 3(BffAuthSessionIT)의 기반 인프라 확보.
