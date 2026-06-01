package com.example.bootstrap.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 도메인 에러 코드 — HTTP status + 사용자 메시지를 함께 보유 (D-18).
 */
@Getter
public enum ErrorCode {

    SAMPLE_NOT_FOUND(HttpStatus.NOT_FOUND, "샘플을 찾을 수 없습니다"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 서버 오류"),

    // 인증 (Phase 2) — 사용자 열거 방지를 위해 INVALID_CREDENTIALS는 email/password 구분 금지
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다"),
    REFRESH_REUSE_DETECTED(HttpStatus.UNAUTHORIZED, "재인증이 필요합니다"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다"),

    // 게시판 (Phase 4) — 도메인 NOT_FOUND/소유권/리액션 target 차단(D-75/D-75b)
    BOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "게시판을 찾을 수 없습니다"),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다"),
    FORBIDDEN_OPERATION(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    REACTION_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "리액션 대상을 찾을 수 없습니다");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }
}
