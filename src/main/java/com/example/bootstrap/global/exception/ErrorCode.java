package com.example.bootstrap.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 도메인별 에러 코드 정의.
 *
 * <p>에러 코드는 도메인 접두사를 포함합니다 (예: AUTH_001, ACCOUNT_001, AI_001).
 * i18n 메시지 키와 동일한 값을 사용합니다.
 */
public enum ErrorCode {

    // ── Auth 도메인 ──────────────────────────────────────────────────────────
    /** 인증 토큰이 없거나 잘못된 형식입니다. */
    AUTH_001(HttpStatus.UNAUTHORIZED, "AUTH_001"),
    /** 만료된 토큰입니다. */
    AUTH_002(HttpStatus.UNAUTHORIZED, "AUTH_002"),
    /** 블랙리스트에 등록된 토큰입니다. */
    AUTH_003(HttpStatus.UNAUTHORIZED, "AUTH_003"),
    /** 이메일 또는 비밀번호가 올바르지 않습니다. */
    AUTH_004(HttpStatus.UNAUTHORIZED, "AUTH_004"),
    /** 이미 사용된 Refresh Token입니다. */
    AUTH_005(HttpStatus.UNAUTHORIZED, "AUTH_005"),
    /** 지원하지 않는 OAuth2 공급자입니다. */
    AUTH_006(HttpStatus.BAD_REQUEST, "AUTH_006"),

    // ── Account 도메인 ───────────────────────────────────────────────────────
    /** 이미 존재하는 이메일입니다. */
    ACCOUNT_001(HttpStatus.CONFLICT, "ACCOUNT_001"),
    /** 존재하지 않는 계정입니다. */
    ACCOUNT_002(HttpStatus.NOT_FOUND, "ACCOUNT_002"),
    /** 권한이 없습니다. */
    ACCOUNT_003(HttpStatus.FORBIDDEN, "ACCOUNT_003"),
    /** OAuth2 전용 계정은 비밀번호 변경 불가입니다. */
    ACCOUNT_004(HttpStatus.BAD_REQUEST, "ACCOUNT_004"),

    // ── AI 도메인 ────────────────────────────────────────────────────────────
    /** AI 서비스 오류가 발생했습니다. */
    AI_001(HttpStatus.INTERNAL_SERVER_ERROR, "AI_001"),
    /** 요청 처리 중 오류가 발생했습니다. */
    AI_002(HttpStatus.BAD_REQUEST, "AI_002"),
    /** AI 모델 응답 시간이 초과되었습니다. */
    AI_003(HttpStatus.GATEWAY_TIMEOUT, "AI_003"),

    // ── Batch 도메인 ─────────────────────────────────────────────────────────
    /** 배치 작업을 실행할 권한이 없습니다. */
    BATCH_001(HttpStatus.FORBIDDEN, "BATCH_001"),
    /** 배치 작업 실행 중 오류가 발생했습니다. */
    BATCH_002(HttpStatus.INTERNAL_SERVER_ERROR, "BATCH_002"),
    /** 이미 실행 중인 배치 작업이 있습니다. */
    BATCH_003(HttpStatus.CONFLICT, "BATCH_003"),

    // ── 공통 ─────────────────────────────────────────────────────────────────
    /** 입력값이 유효하지 않습니다. */
    COMMON_001(HttpStatus.BAD_REQUEST, "COMMON_001"),
    /** 서버 내부 오류가 발생했습니다. */
    COMMON_002(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_002"),
    /** 요청한 리소스를 찾을 수 없습니다. */
    COMMON_003(HttpStatus.NOT_FOUND, "COMMON_003"),
    /** 잘못된 요청 형식입니다. */
    COMMON_004(HttpStatus.BAD_REQUEST, "COMMON_004"),
    /** 지원하지 않는 미디어 타입입니다. */
    COMMON_005(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_005");

    private final HttpStatus httpStatus;
    private final String code;

    ErrorCode(final HttpStatus httpStatus, final String code) {
        this.httpStatus = httpStatus;
        this.code = code;
    }

    /**
     * HTTP 상태 코드를 반환합니다.
     *
     * @return {@link HttpStatus}
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 에러 코드 문자열을 반환합니다.
     *
     * @return 에러 코드 (예: AUTH_001)
     */
    public String getCode() {
        return code;
    }
}
