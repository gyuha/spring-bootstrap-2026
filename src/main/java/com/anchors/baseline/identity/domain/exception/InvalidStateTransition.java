package com.anchors.baseline.identity.domain.exception;

/**
 * 잘못된 상태 전이·멱등성 위반을 표현하는 도메인 예외(D-05).
 * 원시 IllegalStateException 남발 대신 식별 가능한 타입으로 던진다.
 */
public class InvalidStateTransition extends RuntimeException {

    public InvalidStateTransition(String message) {
        super(message);
    }
}
