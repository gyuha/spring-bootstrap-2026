package com.anchors.baseline.authorization.domain.repository;

import com.anchors.baseline.authorization.domain.model.ResourceHierarchy;
import java.util.Optional;

/**
 * ResourceHierarchy 쓰기 포트 — 도메인이 정의하고 infrastructure(JPA)가 구현한다(헥사고날 포트).
 * 도메인 순수 — infrastructure import 금지.
 */
public interface ResourceHierarchyRepository {

    ResourceHierarchy save(ResourceHierarchy hierarchy);

    void deleteByResourceId(long resourceId);

    Optional<ResourceHierarchy> findByResourceId(long resourceId);
}
