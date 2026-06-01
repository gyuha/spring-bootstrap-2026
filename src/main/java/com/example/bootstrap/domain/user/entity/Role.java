package com.example.bootstrap.domain.user.entity;

/**
 * 사용자 역할 (D-33).
 *
 * <p>가입 기본값은 {@link #USER}. ADMIN 부여 경로는 Phase 2 범위 외(시드/Phase 3).
 */
public enum Role {
    USER,
    ADMIN
}
