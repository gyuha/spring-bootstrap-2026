package com.anchors.baseline.authorization.domain.repository;

import com.anchors.baseline.authorization.domain.model.GlobalRoleGrant;
import com.anchors.baseline.authorization.domain.model.RoleName;
import java.util.List;

/**
 * GlobalRoleGrant 쓰기 포트 — 도메인이 정의하고 infrastructure(JPA)가 구현한다(헥사고날 포트).
 * 도메인 순수 — infrastructure import 금지.
 */
public interface GlobalRoleGrantRepository {

    GlobalRoleGrant save(GlobalRoleGrant grant);

    void deleteByUserIdAndRole(long userId, RoleName role);

    List<GlobalRoleGrant> findByUserId(long userId);
}
