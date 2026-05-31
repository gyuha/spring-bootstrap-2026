package com.anchors.baseline.authorization.domain.repository;

import com.anchors.baseline.authorization.domain.model.ResourceGrant;
import java.util.List;

/**
 * ResourceGrant 쓰기 포트 — 도메인이 정의하고 infrastructure(JPA)가 구현한다(헥사고날 포트).
 * 도메인 순수 — infrastructure import 금지.
 */
public interface ResourceGrantRepository {

    ResourceGrant save(ResourceGrant grant);

    void deleteByUserIdAndResourceId(long userId, long resourceId);

    void deleteByGroupIdAndResourceId(long groupId, long resourceId);

    List<ResourceGrant> findByUserId(long userId);

    List<ResourceGrant> findByGroupId(long groupId);
}
