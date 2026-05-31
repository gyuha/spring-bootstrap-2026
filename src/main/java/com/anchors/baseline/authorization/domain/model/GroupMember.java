package com.anchors.baseline.authorization.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * AUTHZ-04: 그룹 멤버십 애그리거트(JPA 방식 A). 다대다 관계를 ID 참조로 표현한다(§5.5).
 * groupId/userId 는 soft reference(물리 FK 없음 — §4.1 / D-07).
 */
@Entity
@Table(name = "group_members")
@Getter
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GroupMember() {
    }

    private GroupMember(long groupId, long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }

    public static GroupMember of(long groupId, long userId) {
        return new GroupMember(groupId, userId);
    }
}
