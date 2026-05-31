package com.anchors.baseline.authorization.infrastructure.jpa;

import com.anchors.baseline.authorization.domain.model.ResourceHierarchy;
import com.anchors.baseline.authorization.domain.repository.ResourceHierarchyRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ResourceHierarchyRepository 포트의 Spring Data JPA 어댑터.
 * deleteByResourceId·findByResourceId 는 파생 쿼리로 자동 구현된다.
 */
public interface ResourceHierarchyJpaRepository
        extends JpaRepository<ResourceHierarchy, Long>, ResourceHierarchyRepository {
}
