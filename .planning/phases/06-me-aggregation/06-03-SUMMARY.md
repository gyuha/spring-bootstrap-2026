# 06-03 SUMMARY — BffAuthMeIT 통합 테스트 (SC#1~3)

**Wave:** 3 | **Requirements:** AUTH-08, AUTH-09, AUTH-11 | **Status:** ✅ Complete

## 무엇을 했는가

`auth/BffAuthMeIT.java` 신규 (5 tests, AbstractIntegrationTest 상속 — Testcontainers postgres:16/redis:7):

- **SC#1** `meReturnsIdentity()` — invite→seededUserId, oidcLogin(user_id=seededUserId) → 200 + userId/email/status("INVITED"). `meUnauthenticatedReturns401()` — 비인증 401.
- **SC#3** `meIsNotPermitAll()` — /api/auth/me 401 + /api/auth/session 200(permitAll 회귀). ArchitectureTest GREEN(전체 스위트에 포함, AUTH-11 SC#3 최종 실증).
- **SC#2** `meReturnsGrantedPermissions()` — 실 Postgres grant 시드(grantGlobalRole ADMIN + grantMenuToUser(1,EDITOR) + grantResourceToUser(10,VIEWER)) → me() roles/menus/resources 단일 응답 단언. `meReturnsEmptyPermissionsForNewUser()` — 권한 없는 사용자 빈 배열 `[]`.

**3자 일치(D-07)**: `identityService.invite()` 반환 실 DB PK를 oidcLogin claim·grant 시드 userId에 모두 사용 — 합성 userId 하드코딩 0(lesson 05). 권한은 실 DB에서 검증.

## 검증

- grant 시그니처 사전 실측: `grantGlobalRole(long, RoleName)`, `grantMenuToUser(long, long, RoleName)`, `grantResourceToUser(long, long, RoleName)`, RoleName=VIEWER/EDITOR/ADMIN 확인.
- `./gradlew test --rerun-tasks` → **63 tests GREEN, 0 실패**(v1.0 51 + Phase5 7 + Phase6 5). BffAuthMeIT 5/5, ArchitectureTest GREEN.
- 설계 단순화: 모든 테스트가 oidcLogin() post-processor + 실 DB grant 시드를 쓰므로 MockOidcServer/WireMock 불필요 — BffAuthSessionIT보다 가벼운 셋업(정당, 실 토큰 교환 불요).

## 비고

- Phase 6 Goal 달성: GET /api/auth/me가 신원(Identity)+직접 권한(Authorization)을 단일 응답으로 교차 집계, ArchUnit 무변경 GREEN으로 AUTH-11 충족.
- D-02 한계(direct only, 그룹/계층 상속 미포함)는 코드 Javadoc + 06-01/06-02 SUMMARY에 명시. 향후 확장 시 3버킷 구조 유지.
