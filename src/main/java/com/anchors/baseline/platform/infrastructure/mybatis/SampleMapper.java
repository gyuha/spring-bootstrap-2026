package com.anchors.baseline.platform.infrastructure.mybatis;

import com.anchors.baseline.platform.application.SampleDto;
import com.anchors.baseline.platform.application.SampleQuery;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * SampleQuery 포트의 MyBatis 어댑터. platform_sample 복잡 조회를 담당한다.
 * @Mapper 로 자동 스캔된다 (mybatis-spring-boot-starter — @MapperScan 불필요).
 */
@Mapper
public interface SampleMapper extends SampleQuery {

    @Override
    @Select("SELECT id, value, created_at FROM platform_sample")
    List<SampleDto> findAll();
}
