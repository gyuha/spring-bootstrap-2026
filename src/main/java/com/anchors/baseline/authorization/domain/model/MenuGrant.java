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
 * AUTHZ-02: 메뉴 권한 부여 애그리거트(JPA 방식 A). 주체는 user XOR group(polymorphic 단일 테이블).
 * XOR 불변식은 코드(forUser/forGroup 팩토리)로 강제하며 DDL CHECK 가 이중 방어한다.
 * userId/groupId/menuId 는 soft reference(물리 FK 없음 — §4.1 / D-07).
 */
@Entity
@Table(name = "menu_grants")
@Getter
public class MenuGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private RoleName role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected MenuGrant() {
    }

    private MenuGrant(Long userId, Long groupId, long menuId, RoleName role) {
        this.userId = userId;
        this.groupId = groupId;
        this.menuId = menuId;
        this.role = role;
    }

    public static MenuGrant forUser(long userId, long menuId, RoleName role) {
        return new MenuGrant(userId, null, menuId, role);
    }

    public static MenuGrant forGroup(long groupId, long menuId, RoleName role) {
        return new MenuGrant(null, groupId, menuId, role);
    }
}
