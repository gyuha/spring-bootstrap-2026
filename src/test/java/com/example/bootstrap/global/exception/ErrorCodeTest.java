package com.example.bootstrap.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * ErrorCode 열거형 단위 테스트.
 *
 * <p>각 에러 코드의 HTTP 상태와 코드 문자열을 검증합니다.
 */
@DisplayName("ErrorCode 열거형 단위 테스트")
class ErrorCodeTest {

    @Test
    @DisplayName("AUTH_001 — UNAUTHORIZED(401) 상태와 'AUTH_001' 코드를 가진다")
    void auth001_hasCorrectStatusAndCode() {
        assertThat(ErrorCode.AUTH_001.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.AUTH_001.getCode()).isEqualTo("AUTH_001");
    }

    @Test
    @DisplayName("ACCOUNT_002 — NOT_FOUND(404) 상태를 가진다")
    void account002_hasNotFoundStatus() {
        assertThat(ErrorCode.ACCOUNT_002.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("ACCOUNT_003 — FORBIDDEN(403) 상태를 가진다")
    void account003_hasForbiddenStatus() {
        assertThat(ErrorCode.ACCOUNT_003.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AI_001 — INTERNAL_SERVER_ERROR(500) 상태를 가진다")
    void ai001_hasInternalServerErrorStatus() {
        assertThat(ErrorCode.AI_001.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @DisplayName("ACCOUNT_001 — CONFLICT(409) 상태를 가진다")
    void account001_hasConflictStatus() {
        assertThat(ErrorCode.ACCOUNT_001.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("모든 에러 코드의 getCode()가 null이 아니다")
    void allErrorCodes_haveNonNullCode() {
        for (final ErrorCode code : ErrorCode.values()) {
            assertThat(code.getCode()).isNotNull().isNotBlank();
            assertThat(code.getHttpStatus()).isNotNull();
        }
    }
}
