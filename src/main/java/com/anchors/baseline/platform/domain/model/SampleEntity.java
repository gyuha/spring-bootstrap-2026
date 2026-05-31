package com.anchors.baseline.platform.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;

/**
 * Phase 1 JPA+MyBatis 통합 검증용 샘플 엔티티 — 업무 도메인과 무관.
 */
@Entity
@Table(name = "platform_sample")
@Getter
public class SampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected SampleEntity() {
    }

    public SampleEntity(String value) {
        this.value = value;
    }
}
