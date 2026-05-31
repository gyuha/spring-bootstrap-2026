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
 * AUTHZ-05: 리소스 계층 애그리거트(JPA 방식 A). resource_id → parent_resource_id 단방향 부모 링크.
 * 상위 리소스 권한의 하위 상속 전개는 읽기 측(MyBatis 재귀 CTE)에서 처리한다(D-05).
 * resourceId/parentResourceId 는 soft reference(물리 FK 없음 — §4.1 / D-07).
 */
@Entity
@Table(name = "resource_hierarchy")
@Getter
public class ResourceHierarchy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(name = "parent_resource_id")
    private Long parentResourceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ResourceHierarchy() {
    }

    private ResourceHierarchy(long resourceId, Long parentResourceId) {
        this.resourceId = resourceId;
        this.parentResourceId = parentResourceId;
    }

    public static ResourceHierarchy of(long resourceId, Long parentResourceId) {
        return new ResourceHierarchy(resourceId, parentResourceId);
    }
}
