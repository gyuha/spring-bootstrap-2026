package com.anchors.baseline.authorization.domain.model;

/**
 * Authorization 도메인 VO — 리소스 식별자(Long 래퍼). null 가드로 입력 검증을 캡슐화한다(§4.3 / ASVS V5).
 * 순수 도메인 — Spring 미참조.
 */
public record ResourceId(Long value) {

    public ResourceId {
        if (value == null) {
            throw new IllegalArgumentException("ResourceId value 는 null 일 수 없습니다");
        }
    }

    public static ResourceId of(long value) {
        return new ResourceId(value);
    }
}
