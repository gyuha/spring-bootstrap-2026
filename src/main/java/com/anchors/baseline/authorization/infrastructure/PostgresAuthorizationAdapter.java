package com.anchors.baseline.authorization.infrastructure;

import com.anchors.baseline.authorization.application.AuthorizationPort;
import com.anchors.baseline.authorization.application.PermissionEvaluator;
import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import com.anchors.baseline.authorization.infrastructure.mybatis.AuthorizationMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * AuthorizationPort 의 Postgres 내부 어댑터(A-5 / D-04). 외부 엔진(OpenFGA 등)으로 교체 가능한 경계.
 * evaluate 는 도메인 판정(PermissionEvaluator — 합산 + 역할 함의)을 재사용하고,
 * listObjects 는 MyBatis 재귀 CTE(AuthorizationMapper)에 위임한다.
 */
@Component
@RequiredArgsConstructor
public class PostgresAuthorizationAdapter implements AuthorizationPort {

    private final PermissionEvaluator permissionEvaluator;
    private final AuthorizationMapper authorizationMapper;

    @Override
    public boolean evaluate(long userId, ResourceId resource, Action action) {
        return permissionEvaluator.evaluate(userId, resource, action);
    }

    @Override
    public List<Long> listObjects(long userId, Action action) {
        return authorizationMapper.listAccessibleResourceIds(userId, action.name());
    }
}
