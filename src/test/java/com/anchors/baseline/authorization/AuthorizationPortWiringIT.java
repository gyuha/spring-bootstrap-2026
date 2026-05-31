package com.anchors.baseline.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.authorization.application.AuthorizationApplicationService;
import com.anchors.baseline.authorization.application.AuthorizationPort;
import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import com.anchors.baseline.authorization.domain.model.RoleName;
import com.anchors.baseline.authorization.infrastructure.PostgresAuthorizationAdapter;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SC#5 (AUTHZ-08): AuthorizationPort 가 주입되며(non-null), 그 구현이 PostgresAuthorizationAdapter 다.
 * 도메인/애플리케이션은 포트(추상)에만 의존하고 구현은 infrastructure 에서 주입 교체 가능한 구조임을 단언한다(A-5).
 * 포트의 evaluate/listObjects 가 실제로 동작함도 함께 확인한다.
 */
class AuthorizationPortWiringIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(5_000_000L);

    private static long nextId() {
        return SEQ.getAndIncrement();
    }

    @Autowired
    AuthorizationPort authorizationPort;

    @Autowired
    AuthorizationApplicationService authzService;

    @Test
    void portIsWiredToPostgresAdapter() {
        assertThat(authorizationPort).isNotNull();
        assertThat(authorizationPort).isInstanceOf(PostgresAuthorizationAdapter.class);
    }

    @Test
    void portEvaluateAndListObjectsWork() {
        long userId = nextId();
        long resourceId = nextId();

        authzService.grantResourceToUser(userId, resourceId, RoleName.VIEWER);

        assertThat(authorizationPort.evaluate(userId, ResourceId.of(resourceId), Action.VIEW)).isTrue();
        assertThat(authorizationPort.listObjects(userId, Action.VIEW)).contains(resourceId);
    }
}
