package com.example.bootstrap.global.exception;

import lombok.Getter;

/**
 * 도메인 비즈니스 예외. {@link ErrorCode}가 HTTP status·메시지를 결정한다 (D-18).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        return errorCode.getMessage();
    }
}
