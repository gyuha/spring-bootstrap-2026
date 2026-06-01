package com.example.bootstrap.global.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 모든 도메인 엔티티의 공통 기반 (FOUND-03).
 *
 * <p>UUIDv7 PK(시간정렬, Hibernate 7.2 내장 incubating), audit 시각({@link Instant} UTC, D-13),
 * soft-delete({@code deleted_at}, D-10), 낙관적 락({@code @Version}, D-11)을 제공한다.
 *
 * <p>{@link SQLRestriction}이 JPA 모든 조회에 {@code deleted_at is null} 조건을 자동 주입한다.
 * MyBatis는 이 필터를 모르므로 모든 조회 SQL에 수동으로 {@code deleted_at IS NULL}을 강제해야 한다(D-10).
 */
@Getter
@MappedSuperclass
@SQLRestriction("deleted_at is null")
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private Instant deletedAt;

    @Version
    private Long version;

    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
