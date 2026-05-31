package com.anchors.baseline.auth.interfaces;

import com.anchors.baseline.authorization.application.PermissionView;
import com.anchors.baseline.identity.application.UserView;
import java.util.List;

/**
 * GET /api/auth/me 응답 계약(AUTH-08/09). 신원(userId/email/status)과 직접 부여 권한(roles/menus/resources)을
 * 단일 record 로 결합해 SPA 가 bootstrap 시 한 번에 받는다.
 *
 * <p>권한 필드는 권한이 없어도 null 이 아닌 빈 배열({@code []})로 직렬화된다.
 *
 * <p><b>D-02 한계:</b> 권한은 직접 부여(direct grant)만 포함하며 그룹·계층 상속은 포함하지 않는다. 향후 그룹/계층
 * 상속을 추가하더라도 이 record 의 3버킷(roles/menus/resources) 구조는 그대로 유지하고 집계 메서드만 확장한다.
 */
public record MeResponse(
        Long userId,
        String email,
        String status,
        List<String> roles,
        List<PermissionView.MenuEntry> menus,
        List<PermissionView.ResourceEntry> resources) {

    /** Wave 1 application read DTO 두 개를 단일 응답으로 결합한다. */
    public static MeResponse of(UserView identity, PermissionView permissions) {
        return new MeResponse(
                identity.id(),
                identity.email(),
                identity.status(),
                permissions.roles(),
                permissions.menus(),
                permissions.resources());
    }
}
