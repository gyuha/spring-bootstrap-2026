package com.anchors.baseline.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.authorization.application.AuthorizationApplicationService;
import com.anchors.baseline.authorization.application.AuthorizationPort;
import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.RoleName;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SC#3: 상위 리소스 부여 → 하위 리소스가 ListObjects 에 포함됨(하향 재귀 CTE 전개 — 실 Postgres).
 *
 * <p>defineHierarchy(child, parent) 로 child→parent 링크를 만든 뒤 parent 에 권한을 부여하면,
 * listAccessibleResourceIds 의 descendants CTE 가 parent→child 로 전개되어 child 가 결과에 포함된다.
 */
class ListObjectsIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(2_000_000L);

    private static long nextId() {
        return SEQ.getAndIncrement();
    }

    @Autowired
    AuthorizationApplicationService authzService;

    @Autowired
    AuthorizationPort authorizationPort;

    @Test
    void parentGrantSurfacesChildInListObjects() {
        long userId = nextId();
        long parent = nextId();
        long child = nextId();

        authzService.defineHierarchy(child, parent);
        authzService.grantResourceToUser(userId, parent, RoleName.VIEWER);

        List<Long> accessible = authorizationPort.listObjects(userId, Action.VIEW);

        assertThat(accessible).contains(parent, child);
    }

    @Test
    void unrelatedResourceIsNotListed() {
        long userId = nextId();
        long granted = nextId();
        long unrelated = nextId();

        authzService.grantResourceToUser(userId, granted, RoleName.VIEWER);

        List<Long> accessible = authorizationPort.listObjects(userId, Action.VIEW);

        assertThat(accessible).contains(granted).doesNotContain(unrelated);
    }
}
