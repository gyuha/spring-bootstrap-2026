package com.anchors.baseline.authorization.infrastructure.jpa;

import com.anchors.baseline.authorization.domain.model.MenuGrant;
import com.anchors.baseline.authorization.domain.repository.MenuGrantRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * MenuGrantRepository 포트의 Spring Data JPA 어댑터.
 * deleteByUserIdAndMenuId·deleteByGroupIdAndMenuId·findByUserId·findByGroupId 는 파생 쿼리로 자동 구현된다.
 */
public interface MenuGrantJpaRepository
        extends JpaRepository<MenuGrant, Long>, MenuGrantRepository {
}
