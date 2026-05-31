package com.anchors.baseline.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.anchors.baseline.authorization.application.EffectiveGrantDto;
import com.anchors.baseline.authorization.application.PermissionEvaluator;
import com.anchors.baseline.authorization.application.PermissionReadPort;
import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * PermissionEvaluator 순수 단위 테스트 — Spring/Docker 없이 in-memory fake PermissionReadPort 주입(POJO).
 *
 * <p>합산 판정(SC#2)의 핵심인 역할 함의(RoleName.implies)와 default-deny(ASVS V4)를 데이터 수집과
 * 분리해 검증한다. CTE 합산(직접 ∪ 그룹 ∪ 상속)은 통합 테스트(IT)에서 실 Postgres 로 검증한다.
 */
class PermissionEvaluatorTest {

    private static final long USER_ID = 7L;
    private static final ResourceId RESOURCE = ResourceId.of(42L);

    /** 고정 grant 목록을 돌려주는 fake — DB/Spring 없이 evaluate 로직만 격리한다. */
    private static PermissionReadPort fakePort(List<EffectiveGrantDto> grants) {
        return new PermissionReadPort() {
            @Override
            public List<EffectiveGrantDto> findEffectiveGrants(long userId, long resourceId) {
                return grants;
            }

            @Override
            public List<Long> listAccessibleResourceIds(long userId, String action) {
                throw new UnsupportedOperationException("not used in this unit test");
            }
        };
    }

    private static EffectiveGrantDto grant(String role) {
        return new EffectiveGrantDto(role, RESOURCE.value(), "direct");
    }

    @Test
    void emptyGrantsAreDeniedByDefault() {
        PermissionEvaluator evaluator = new PermissionEvaluator(fakePort(List.of()));

        assertThat(evaluator.evaluate(USER_ID, RESOURCE, Action.VIEW)).isFalse();
        assertThat(evaluator.evaluate(USER_ID, RESOURCE, Action.EDIT)).isFalse();
    }

    @Test
    void editorGrantImpliesViewAction() {
        PermissionEvaluator evaluator = new PermissionEvaluator(fakePort(List.of(grant("EDITOR"))));

        assertThat(evaluator.evaluate(USER_ID, RESOURCE, Action.VIEW)).isTrue();
    }

    @Test
    void viewerGrantDoesNotImplyEditAction() {
        PermissionEvaluator evaluator = new PermissionEvaluator(fakePort(List.of(grant("VIEWER"))));

        assertThat(evaluator.evaluate(USER_ID, RESOURCE, Action.EDIT)).isFalse();
    }

    /**
     * 역할 함의 진리표 — VIEWER → {VIEW}, EDITOR/ADMIN → {VIEW, EDIT} (§5.4).
     */
    @ParameterizedTest(name = "{0} grant + {1} action → {2}")
    @CsvSource({
        "VIEWER, VIEW, true",
        "VIEWER, EDIT, false",
        "EDITOR, VIEW, true",
        "EDITOR, EDIT, true",
        "ADMIN,  VIEW, true",
        "ADMIN,  EDIT, true",
    })
    void implicationMatrix(String role, Action action, boolean expected) {
        PermissionEvaluator evaluator = new PermissionEvaluator(fakePort(List.of(grant(role))));

        assertThat(evaluator.evaluate(USER_ID, RESOURCE, action)).isEqualTo(expected);
    }
}
