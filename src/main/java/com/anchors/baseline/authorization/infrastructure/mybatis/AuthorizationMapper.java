package com.anchors.baseline.authorization.infrastructure.mybatis;

import com.anchors.baseline.authorization.application.EffectiveGrantDto;
import com.anchors.baseline.authorization.application.PermissionReadPort;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * PermissionReadPort 포트의 MyBatis 어댑터(재귀 CTE 읽기 모델, D-05).
 * 합산 판정·계층 상속·ListObjects 의 재귀 CTE 는 AuthorizationMapper.xml(동명 namespace)에 둔다 —
 * 다중 행·복잡 SQL 은 XML 가독성이 유리하다. @Mapper 로 자동 스캔된다(@MapperScan 불필요).
 * 포트 메서드를 @Param 으로 재선언해 MyBatis 바인딩을 인프라에 가둔다 — application 은 MyBatis 무지(헥사고날).
 */
@Mapper
public interface AuthorizationMapper extends PermissionReadPort {

    @Override
    List<EffectiveGrantDto> findEffectiveGrants(@Param("userId") long userId,
                                                @Param("resourceId") long resourceId);

    @Override
    List<Long> listAccessibleResourceIds(@Param("userId") long userId,
                                         @Param("action") String action);
}
