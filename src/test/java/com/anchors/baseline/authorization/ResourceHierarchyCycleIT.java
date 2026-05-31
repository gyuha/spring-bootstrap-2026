package com.anchors.baseline.authorization;

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.authorization.application.AuthorizationApplicationService;
import com.anchors.baseline.authorization.application.AuthorizationPort;
import com.anchors.baseline.authorization.application.PermissionEvaluator;
import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import com.anchors.baseline.authorization.domain.model.RoleName;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 사이클 가드 — resource_hierarchy 에 A.parent=B, B.parent=A 사이클을 넣어도 재귀 CTE 가 무한 루프하지 않음을
 * 증명한다(findEffectiveGrants 의 CYCLE 절 / listAccessibleResourceIds 의 UNION 중복 제거 — Pitfall 1).
 *
 * <p>이 방어는 실 Postgres 에서만 재현된다(H2 금지, D-10). assertTimeoutPreemptively 로 5초 내 정상 종료를
 * 강제해, 가드가 빠지면(무한 재귀) 테스트가 타임아웃으로 깨지게 한다(틀리면 깨지는 테스트 — lesson 03).
 */
class ResourceHierarchyCycleIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(3_000_000L);

    private static long nextId() {
        return SEQ.getAndIncrement();
    }

    @Autowired
    AuthorizationApplicationService authzService;

    @Autowired
    PermissionEvaluator permissionEvaluator;

    @Autowired
    AuthorizationPort authorizationPort;

    @Test
    void cyclicHierarchyDoesNotInfiniteLoop() {
        long userId = nextId();
        long a = nextId();
        long b = nextId();

        // 사이클 구성: A.parent = B, B.parent = A (UNIQUE(resource_id) 라 서로 다른 행이므로 둘 다 INSERT 성공)
        authzService.defineHierarchy(a, b);
        authzService.defineHierarchy(b, a);
        authzService.grantResourceToUser(userId, a, RoleName.VIEWER);

        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            // findEffectiveGrants: A→B→A... 조상 체인을 CYCLE 절이 끊어야 한다
            permissionEvaluator.evaluate(userId, ResourceId.of(a), Action.VIEW);
            permissionEvaluator.evaluate(userId, ResourceId.of(b), Action.VIEW);
            // listAccessibleResourceIds: descendants UNION 이 사이클을 끊어야 한다
            authorizationPort.listObjects(userId, Action.VIEW);
        });
    }
}
