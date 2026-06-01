package com.example.bootstrap.domain.sample.repository;

import com.example.bootstrap.domain.sample.entity.Sample;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Sample JPA 저장소 (쓰기/단건 CRUD, D-08).
 *
 * <p>BaseEntity의 {@code @SQLRestriction("deleted_at is null")}이 모든 조회에 자동 적용되므로,
 * soft-delete된 행은 findById/findAll에서 자동 제외된다.
 */
public interface SampleRepository extends JpaRepository<Sample, UUID> {
}
