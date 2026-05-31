package com.anchors.baseline.authorization.infrastructure.jpa;

import com.anchors.baseline.authorization.domain.model.GroupMember;
import com.anchors.baseline.authorization.domain.repository.GroupMemberRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * GroupMemberRepository 포트의 Spring Data JPA 어댑터.
 * deleteByGroupIdAndUserId·findByUserId·findByGroupId 는 파생 쿼리로 자동 구현된다.
 */
public interface GroupMemberJpaRepository
        extends JpaRepository<GroupMember, Long>, GroupMemberRepository {
}
