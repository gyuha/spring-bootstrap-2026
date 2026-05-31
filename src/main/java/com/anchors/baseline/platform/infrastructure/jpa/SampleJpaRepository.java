package com.anchors.baseline.platform.infrastructure.jpa;

import com.anchors.baseline.platform.domain.model.SampleEntity;
import com.anchors.baseline.platform.domain.repository.SampleRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SampleRepository 포트의 Spring Data JPA 어댑터.
 * JpaRepository 가 save(SampleEntity) 를 기본 제공하므로 포트 구현을 그대로 만족한다.
 */
public interface SampleJpaRepository
        extends JpaRepository<SampleEntity, Long>, SampleRepository {
}
