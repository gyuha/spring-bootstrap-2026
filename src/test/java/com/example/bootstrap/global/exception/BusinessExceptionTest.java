package com.example.bootstrap.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BusinessException 단위 테스트.
 *
 * <p>비즈니스 예외 생성과 에러 코드 접근을 검증합니다.
 */
@DisplayName("BusinessException 단위 테스트")
class BusinessExceptionTest {

    @Test
    @DisplayName("ErrorCode만으로 생성된 예외의 메시지는 에러 코드 값과 동일하다")
    void constructor_withErrorCode_messageIsErrorCode() {
        final BusinessException ex = new BusinessException(ErrorCode.AUTH_001);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_001);
        assertThat(ex.getMessage()).isEqualTo("AUTH_001");
    }

    @Test
    @DisplayName("ErrorCode와 사용자 정의 메시지로 생성된 예외의 메시지가 올바르다")
    void constructor_withErrorCodeAndMessage_hasCustomMessage() {
        final String customMessage = "Custom error detail";
        final BusinessException ex = new BusinessException(ErrorCode.ACCOUNT_002, customMessage);

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_002);
        assertThat(ex.getMessage()).isEqualTo(customMessage);
    }

    @Test
    @DisplayName("getErrorCode()가 생성 시 전달한 ErrorCode를 반환한다")
    void getErrorCode_returnsProvidedErrorCode() {
        final BusinessException ex = new BusinessException(ErrorCode.AI_001);

        assertThat(ex.getErrorCode()).isSameAs(ErrorCode.AI_001);
    }
}
