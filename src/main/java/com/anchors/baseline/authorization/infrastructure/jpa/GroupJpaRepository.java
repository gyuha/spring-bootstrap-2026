package com.anchors.baseline.authorization.infrastructure.jpa;

import com.anchors.baseline.authorization.domain.model.Group;
import com.anchors.baseline.authorization.domain.repository.GroupRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GroupRepository 포트의 Spring Data JPA 어댑터.
 * save/findById 는 JpaRepository 기본 제공, findByName 은 파생 쿼리로 자동 구현된다.
 */
public interface GroupJpaRepository
        extends JpaRepository<Group, Long>, GroupRepository {
}
