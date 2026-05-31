# 06-01 SUMMARY — application read 메서드 + DTO

**Wave:** 1 | **Requirements:** AUTH-08, AUTH-09 | **Status:** ✅ Complete

## 무엇을 했는가

types-first 계약 확정 (Wave 2 AuthController.me()가 호출):

- **UserView**(record, identity/application) — `Long id, String email, String status`. User/Email/UserStatus 평탄화, interfaces 미노출. 영속 애너테이션 없음.
- **IdentityApplicationService.findUser(long)** — `@Transactional(readOnly=true)`. `userRepository.findById(userId).orElseThrow(IllegalArgumentException)` → UserView. 기존 invite/linkIdentity/disable 무변경.
- **PermissionView**(record, authorization/application) — `List<String> roles, List<MenuEntry> menus, List<ResourceEntry> resources` + 중첩 `MenuEntry(long menuId, String role)`/`ResourceEntry(long resourceId, String role)`. **D-02 direct 한계를 Javadoc에 명시**(그룹/계층 상속 미포함, 3버킷 구조 유지·집계 메서드만 확장).
- **AuthorizationApplicationService.findPermissions(long)** — `@Transactional(readOnly=true)`. `findByUserId` 3종만 호출(`findByGroupId` 미사용, D-02 주석). RoleName.name() 평탄화, 빈 권한은 빈 List.

## 검증

- 도메인 시그니처 사전 실측: findByUserId(long) 3종, @Getter(getRole→RoleName/getMenuId/getResourceId→Long), User getId/getEmail/getStatus 확인(Phase 4 "추정" 리스크 회피).
- `./gradlew compileJava` 성공. `./gradlew test --rerun-tasks` → **58 tests GREEN, 0 실패**(v1.0 51 + Phase 5 7, 무회귀).
- `java.util.List` import 추가(findPermissions 사용).

## 비고

- 영속 기술 애너테이션 누수 0(Phase 4 lesson). interfaces에 도메인 타입 노출 0.
- Wave 2(MeResponse+me())·Wave 3(BffAuthMeIT)의 application read 계약 확정.
