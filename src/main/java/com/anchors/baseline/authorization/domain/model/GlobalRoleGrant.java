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
 * AUTHZ-01: 전역 역할 부여 애그리거트(JPA 방식 A). userId 에 전역 역할을 부여한다.
 * userId 는 identity 컨텍스트 User.id 로의 soft reference(물리 FK 없음 — §4.1 / D-07).
 * public setter 금지, 정적 팩토리로만 생성(D-01).
 */
@Entity
@Table(name = "global_role_grants")
@Getter
public class GlobalRoleGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private RoleName role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected GlobalRoleGrant() {
    }

    private GlobalRoleGrant(long userId, RoleName role) {
        this.userId = userId;
        this.role = role;
    }

    public static GlobalRoleGrant of(long userId, RoleName role) {
        return new GlobalRoleGrant(userId, role);
    }
}
