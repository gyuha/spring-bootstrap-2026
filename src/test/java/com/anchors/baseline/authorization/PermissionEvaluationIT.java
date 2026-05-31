package com.anchors.baseline.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.authorization.application.AuthorizationApplicationService;
import com.anchors.baseline.authorization.application.PermissionEvaluator;
import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import com.anchors.baseline.authorization.domain.model.RoleName;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.stream.Stream;

/**
 * SC#1(즉시성) + SC#2(진리표) 통합 테스트 — 실 Postgres(Testcontainers).
 *
 * <p>Pitfall 3: grant/revoke 는 {@link AuthorizationApplicationService} 호출로 자체 트랜잭션에서 커밋된 뒤,
 * 별 호출인 {@link PermissionEvaluator#evaluate}(MyBatis 읽기)가 커밋된 데이터를 본다 — read-after-write 회피.
 *
 * <p>각 테스트는 nanoTime 기반 고유 userId/groupId/resourceId 로 격리한다(@SpringBootTest 는 롤백하지 않음).
 */
class PermissionEvaluationIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(1_000_000L);

    private static long nextId() {
        return SEQ.getAndIncrement();
    }

    @Autowired
    AuthorizationApplicationService authzService;

    @Autowired
    PermissionEvaluator permissionEvaluator;

    /**
     * SC#1: grant(커밋) → evaluate=true, revoke(커밋) → evaluate=false 연속 단언(캐시 없음, 즉시 반영 — D-06).
     */
    @Test
    void grantThenRevokeIsImmediatelyReflected() {
        long userId = nextId();
        ResourceId resource = ResourceId.of(nextId());

        assertThat(permissionEvaluator.evaluate(userId, resource, Action.VIEW)).isFalse();

        authzService.grantResourceToUser(userId, resource.value(), RoleName.VIEWER);
        assertThat(permissionEvaluator.evaluate(userId, resource, Action.VIEW)).isTrue();

        authzService.revokeResourceFromUser(userId, resource.value());
        assertThat(permissionEvaluator.evaluate(userId, resource, Action.VIEW)).isFalse();
    }

    /**
     * SC#2 진리표: 직접만 / 그룹만 / 상속만 / 조합 / 없음(default-deny) + 역할 함의(EDITOR→VIEW).
     * 각 케이스는 독립 userId/resourceId 로 격리하고, 부여 경로를 람다로 세팅한 뒤 evaluate 결과를 단언한다.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("truthTableCases")
    void evaluateTruthTable(String name, GrantSetup setup, Action action, boolean expected) {
        long userId = nextId();
        long resourceId = nextId();

        setup.apply(this, userId, resourceId);

        assertThat(permissionEvaluator.evaluate(userId, ResourceId.of(resourceId), action))
            .isEqualTo(expected);
    }

    @FunctionalInterface
    interface GrantSetup {
        void apply(PermissionEvaluationIT test, long userId, long resourceId);
    }

    static Stream<Arguments> truthTableCases() {
        // 직접 부여만 — VIEWER 직접 부여, VIEW 허용
        GrantSetup directOnly =
            (t, u, r) -> t.authzService.grantResourceToUser(u, r, RoleName.VIEWER);

        // 그룹 부여만 — 그룹 생성 → 멤버 추가 → 그룹에 리소스 부여
        GrantSetup groupOnly = (t, u, r) -> {
            long groupId = t.authzService.createGroup("grp-" + System.nanoTime());
            t.authzService.addMember(groupId, u);
            t.authzService.grantResourceToGroup(groupId, r, RoleName.VIEWER);
        };

        // 상속만 — 부모 리소스 생성 + 부모에 부여, child 의 계층 부모로 연결
        GrantSetup inheritedOnly = (t, u, r) -> {
            long parent = nextId();
            t.authzService.defineHierarchy(r, parent);
            t.authzService.grantResourceToUser(u, parent, RoleName.VIEWER);
        };

        // 조합 — 직접 + 그룹 + 상속 모두
        GrantSetup combined = (t, u, r) -> {
            t.authzService.grantResourceToUser(u, r, RoleName.VIEWER);
            long groupId = t.authzService.createGroup("grp-" + System.nanoTime());
            t.authzService.addMember(groupId, u);
            t.authzService.grantResourceToGroup(groupId, r, RoleName.VIEWER);
            long parent = nextId();
            t.authzService.defineHierarchy(r, parent);
            t.authzService.grantResourceToUser(u, parent, RoleName.VIEWER);
        };

        // 없음 — 아무 부여 없음(default-deny)
        GrantSetup none = (t, u, r) -> {
            // intentionally empty
        };

        // 역할 함의 — EDITOR 직접 부여, VIEW 도 허용(EDITOR ⊇ VIEWER)
        GrantSetup editorImpliesView =
            (t, u, r) -> t.authzService.grantResourceToUser(u, r, RoleName.EDITOR);

        // 역할 함의 음성 — VIEWER 직접 부여, EDIT 은 불허
        GrantSetup viewerNoEdit =
            (t, u, r) -> t.authzService.grantResourceToUser(u, r, RoleName.VIEWER);

        return Stream.of(
            Arguments.of("direct-only VIEW → true", directOnly, Action.VIEW, true),
            Arguments.of("group-only VIEW → true", groupOnly, Action.VIEW, true),
            Arguments.of("inherited-only VIEW → true", inheritedOnly, Action.VIEW, true),
            Arguments.of("combined VIEW → true", combined, Action.VIEW, true),
            Arguments.of("none VIEW → false (default-deny)", none, Action.VIEW, false),
            Arguments.of("EDITOR grant + VIEW → true (implication)", editorImpliesView, Action.VIEW, true),
            Arguments.of("VIEWER grant + EDIT → false (no implication)", viewerNoEdit, Action.EDIT, false)
        );
    }
}
