package com.anchors.baseline.authorization.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * AUTHZ-03/06: 리소스 권한 부여 애그리거트(JPA 방식 A). 주체는 user XOR group(polymorphic 단일 테이블).
 * XOR 불변식은 코드(forUser/forGroup 팩토리)로 강제하며 DDL CHECK 가 이중 방어한다.
 * userId/groupId/resourceId 는 soft reference(물리 FK 없음 — §4.1 / D-07).
 */
@Entity
@Table(name = "resource_grants")
@Getter
public class ResourceGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private RoleName role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ResourceGrant() {
    }

    private ResourceGrant(Long userId, Long groupId, long resourceId, RoleName role) {
        this.userId = userId;
        this.groupId = groupId;
        this.resourceId = resourceId;
        this.role = role;
    }

    public static ResourceGrant forUser(long userId, long resourceId, RoleName role) {
        return new ResourceGrant(userId, null, resourceId, role);
    }

    public static ResourceGrant forGroup(long groupId, long resourceId, RoleName role) {
        return new ResourceGrant(null, groupId, resourceId, role);
    }
}
