package com.example.bootstrap.domain.sample.mapper;

import com.example.bootstrap.domain.sample.dto.SampleResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * Sample 복잡 조회용 MyBatis 매퍼 (FOUND-02, D-08).
 *
 * <p>JPA의 {@code @SQLRestriction}이 MyBatis에는 적용되지 않으므로, 모든 조회 SQL은
 * {@code deleted_at IS NULL}을 수동으로 강제해야 한다(D-10). 실제 SQL은 동명의 XML 매퍼에 정의된다.
 */
@Mapper
public interface SampleQueryMapper {

    List<SampleResponse> findAll();

    long countAll();
}
