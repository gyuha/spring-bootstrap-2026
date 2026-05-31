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
 * AUTHZ-04: 그룹 애그리거트(JPA 방식 A). 앱 자체 관리(§5.5 — 외부 IdP 그룹 동기화 아님).
 * 멤버십은 별도 애그리거트(GroupMember, ID 참조 다대다)로 분리한다(§5.4 작은 애그리거트).
 */
@Entity
@Table(name = "groups")
@Getter
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Group() {
    }

    private Group(String name) {
        this.name = name;
    }

    public static Group create(String name) {
        return new Group(name);
    }
}
