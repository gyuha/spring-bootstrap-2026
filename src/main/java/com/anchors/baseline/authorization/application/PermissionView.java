package com.anchors.baseline.authorization.application;

import java.util.List;

/**
 * Authorization 컨텍스트 직접 부여 권한 읽기 DTO (CQRS-lite, AUTH-09 / D-02). BFF /api/auth/me 집계용.
 *
 * <p><b>D-02 direct 해석:</b> 사용자에게 <b>직접 부여(direct)</b>된 전역 역할·메뉴·리소스 권한만 포함하며,
 * 그룹 멤버십·리소스 계층(재귀 CTE)을 통한 <b>상속 권한은 포함하지 않는다</b>. 기존 읽기 모델
 * ({@code findEffectiveGrants(userId, resourceId)})는 리소스 단위 판정용이라 전체 열거에 재사용 불가하다.
 * 향후 그룹/계층 상속을 추가하더라도 roles/menus/resources 3버킷 구조는 유지하고 집계 메서드만 확장한다.
 *
 * <p>영속 기술 애너테이션(@Entity/@Param 등)을 두지 않는다 — 순수 read DTO. 권한이 없으면 빈 List(null 아님).
 */
public record PermissionView(List<String> roles, List<MenuEntry> menus, List<ResourceEntry> resources) {

    /** 메뉴 권한 항목 — 메뉴 id + 역할명. */
    public record MenuEntry(long menuId, String role) {
    }

    /** 리소스 권한 항목 — 리소스 id + 역할명. */
    public record ResourceEntry(long resourceId, String role) {
    }
}
