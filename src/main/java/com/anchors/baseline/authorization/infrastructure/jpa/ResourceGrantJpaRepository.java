package com.anchors.baseline.authorization.infrastructure.jpa;

import com.anchors.baseline.authorization.domain.model.ResourceGrant;
import com.anchors.baseline.authorization.domain.repository.ResourceGrantRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ResourceGrantRepository 포트의 Spring Data JPA 어댑터.
 * deleteByUserIdAndResourceId·deleteByGroupIdAndResourceId·findByUserId·findByGroupId 는 파생 쿼리로 자동 구현된다.
 */
public interface ResourceGrantJpaRepository
        extends JpaRepository<ResourceGrant, Long>, ResourceGrantRepository {
}
