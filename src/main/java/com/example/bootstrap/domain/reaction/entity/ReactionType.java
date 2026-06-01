package com.example.bootstrap.domain.reaction.entity;

/**
 * 리액션 종류 (BOARD-06/CMNT-05, D-73).
 *
 * <p>순수 Java enum이다 — JPA {@code @Entity}가 아니다. reaction은 MyBatis 전용(04-04)이며 이
 * enum은 매퍼 파라미터·서비스 시그니처·DTO에서 {@code .name()} 문자열로 바인딩된다.
 */
public enum ReactionType {
    LIKE,
    DISLIKE
}
