package com.anchors.baseline.authorization.domain.model;

/**
 * Authorization 도메인 VO — 리소스에 대한 행위(액션). 순수 도메인(Spring 미참조).
 * 역할 함의(RoleName.implies)의 입력이며, 권한 판정의 최소 단위다(§5.4).
 */
public enum Action {
    VIEW,
    EDIT
}
