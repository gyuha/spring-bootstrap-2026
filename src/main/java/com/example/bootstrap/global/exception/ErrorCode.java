package com.example.bootstrap.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 도메인 에러 코드 — HTTP status + 사용자 메시지를 함께 보유 (D-18).
 */
@Getter
public enum ErrorCode {

    SAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "샘플을 찾을 수 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
