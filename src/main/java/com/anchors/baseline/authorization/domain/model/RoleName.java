package com.anchors.baseline.authorization.domain.model;

/**
 * Authorization 도메인 VO — 역할. 역할 함의(EDITOR ⊇ VIEWER 등)를 도메인에 캡슐화한다(§5.4 / D-05).
 * 순수 도메인 — Spring/프레임워크 미참조. 함의 규칙은 코드로 표현하며 외부 데이터에 의존하지 않는다.
 *
 * <p>함의 모델: VIEWER → {VIEW}, EDITOR → {VIEW, EDIT}, ADMIN → {VIEW, EDIT}.
 * EDITOR 는 VIEWER 의 능력을 포함하고(EDITOR ⊇ VIEWER), ADMIN 은 EDITOR 를 포함한다.
 */
public enum RoleName {
    VIEWER,
    EDITOR,
    ADMIN;

    /**
     * 이 역할이 주어진 액션을 허용하는지 — 역할 함의 규칙(§5.4).
     */
    public boolean implies(Action action) {
        return switch (this) {
            case VIEWER -> action == Action.VIEW;
            case EDITOR, ADMIN -> action == Action.VIEW || action == Action.EDIT;
        };
    }
}
