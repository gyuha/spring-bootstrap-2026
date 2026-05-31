package com.anchors.baseline.authorization.domain.model;

/**
 * Authorization 도메인 VO — 메뉴 식별자(Long 래퍼). null 가드로 입력 검증을 캡슐화한다(§4.3 / ASVS V5).
 * 순수 도메인 — Spring 미참조.
 */
public record MenuId(Long value) {

    public MenuId {
        if (value == null) {
            throw new IllegalArgumentException("MenuId value 는 null 일 수 없습니다");
        }
    }

    public static MenuId of(long value) {
        return new MenuId(value);
    }
}
