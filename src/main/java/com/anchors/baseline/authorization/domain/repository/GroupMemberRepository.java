package com.anchors.baseline.authorization.domain.repository;

import com.anchors.baseline.authorization.domain.model.GroupMember;
import java.util.List;

/**
 * GroupMember 쓰기 포트 — 도메인이 정의하고 infrastructure(JPA)가 구현한다(헥사고날 포트).
 * 도메인 순수 — infrastructure import 금지.
 */
public interface GroupMemberRepository {

    GroupMember save(GroupMember member);

    void deleteByGroupIdAndUserId(long groupId, long userId);

    List<GroupMember> findByUserId(long userId);

    List<GroupMember> findByGroupId(long groupId);
}
