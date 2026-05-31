package com.anchors.baseline.identity.application;

/**
 * 초대 시점 email 중복 선검사 위반(D-06 UX 선검사). application 계층에 둔다 —
 * DB UNIQUE 가 동시성 최종 방어이고, 선검사 예외는 유스케이스 조율의 일부다.
 */
public class EmailAlreadyExists extends RuntimeException {

    public EmailAlreadyExists(String message) {
        super(message);
    }
}
