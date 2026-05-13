package com.example.bootstrap.global.exception;

/**
 * 비즈니스 로직 예외 클래스.
 *
 * <p>도메인별 에러 코드를 포함하는 비즈니스 예외입니다.
 * GlobalExceptionHandler에서 처리됩니다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * 에러 코드로 예외를 생성합니다.
     *
     * @param errorCode 에러 코드
     */
    public BusinessException(final ErrorCode errorCode) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
    }

    /**
     * 에러 코드와 메시지로 예외를 생성합니다.
     *
     * @param errorCode 에러 코드
     * @param message   상세 메시지
     */
    public BusinessException(final ErrorCode errorCode, final String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 에러 코드를 반환합니다.
     *
     * @return {@link ErrorCode}
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
