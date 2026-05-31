package com.anchors.baseline.authorization.infrastructure.mybatis;

import com.anchors.baseline.authorization.application.PermissionReadPort;
import org.apache.ibatis.annotations.Mapper;

/**
 * PermissionReadPort 포트의 MyBatis 어댑터(재귀 CTE 읽기 모델, D-05).
 * 합산 판정·계층 상속·ListObjects 의 재귀 CTE 는 AuthorizationMapper.xml(동명 namespace)에 둔다 —
 * 다중 행·복잡 SQL 은 XML 가독성이 유리하다. @Mapper 로 자동 스캔된다(@MapperScan 불필요).
 */
@Mapper
public interface AuthorizationMapper extends PermissionReadPort {
}
