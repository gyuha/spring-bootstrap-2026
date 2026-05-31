package com.anchors.baseline.authorization.domain.repository;

import com.anchors.baseline.authorization.domain.model.MenuGrant;
import java.util.List;

/**
 * MenuGrant 쓰기 포트 — 도메인이 정의하고 infrastructure(JPA)가 구현한다(헥사고날 포트).
 * 도메인 순수 — infrastructure import 금지.
 */
public interface MenuGrantRepository {

    MenuGrant save(MenuGrant grant);

    void deleteByUserIdAndMenuId(long userId, long menuId);

    void deleteByGroupIdAndMenuId(long groupId, long menuId);

    List<MenuGrant> findByUserId(long userId);

    List<MenuGrant> findByGroupId(long groupId);
}
