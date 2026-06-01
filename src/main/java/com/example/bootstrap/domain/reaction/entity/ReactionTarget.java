package com.example.bootstrap.domain.reaction.entity;

/**
 * 리액션 대상 종류 (BOARD-06/CMNT-05, D-73).
 *
 * <p>순수 Java enum이다 — JPA {@code @Entity}가 아니다. reactions 테이블 {@code target_type}
 * 컬럼에 {@code .name()} 문자열(POST/COMMENT)로 MyBatis(04-04)가 바인딩한다.
 */
public enum ReactionTarget {
    POST,
    COMMENT
}
