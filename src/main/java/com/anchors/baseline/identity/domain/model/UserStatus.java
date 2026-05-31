package com.anchors.baseline.identity.domain.model;

/**
 * 사용자 식별 상태 — @Enumerated(STRING) 매핑(D-02).
 * INVITED(초대됨) → ACTIVE(신원 연결됨) → DISABLED(비활성화).
 */
public enum UserStatus {
    INVITED,
    ACTIVE,
    DISABLED
}
