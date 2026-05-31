package com.anchors.baseline.authorization.infrastructure.jpa;

import com.anchors.baseline.authorization.domain.model.GlobalRoleGrant;
import com.anchors.baseline.authorization.domain.repository.GlobalRoleGrantRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GlobalRoleGrantRepository 포트의 Spring Data JPA 어댑터.
 * save/findById 는 JpaRepository 기본 제공, deleteByUserIdAndRole·findByUserId 는 파생 쿼리로 자동 구현된다.
 */
public interface GlobalRoleGrantJpaRepository
        extends JpaRepository<GlobalRoleGrant, Long>, GlobalRoleGrantRepository {
}
