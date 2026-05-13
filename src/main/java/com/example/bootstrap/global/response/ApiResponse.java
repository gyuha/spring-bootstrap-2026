package com.example.bootstrap.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * API 응답 Envelope 클래스.
 *
 * <p>모든 API 응답은 이 클래스를 통해 {@code {code, message, data, errors}} 형식으로 반환됩니다.
 *
 * @param <T> 응답 데이터 타입
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    String code,
    String message,
    T data,
    List<FieldError> errors
) {

    /**
     * 방어적 복사 compact constructor.
     *
     * <p>외부에서 전달된 가변 컬렉션을 불변 복사본으로 저장합니다.
     *
     * @param code    응답 코드
     * @param message 응답 메시지
     * @param data    응답 데이터
     * @param errors  유효성 검증 에러 목록
     */
    public ApiResponse {
        errors = errors != null ? List.copyOf(errors) : null;
    }

    /**
     * 필드 유효성 검증 에러를 표현하는 레코드.
     *
     * @param field  오류가 발생한 필드명
     * @param reason 오류 사유
     */
    public record FieldError(String field, String reason) {
    }

    /**
     * 성공 응답을 생성합니다 (데이터 없음).
     *
     * @param message 응답 메시지
     * @param <T>     응답 데이터 타입
     * @return 성공 응답
     */
    public static <T> ApiResponse<T> success(final String message) {
        return new ApiResponse<>("SUCCESS", message, null, null);
    }

    /**
     * 데이터가 있는 성공 응답을 생성합니다.
     *
     * @param message 응답 메시지
     * @param data    응답 데이터
     * @param <T>     응답 데이터 타입
     * @return 성공 응답
     */
    public static <T> ApiResponse<T> success(final String message, final T data) {
        return new ApiResponse<>("SUCCESS", message, data, null);
    }

    /**
     * 에러 응답을 생성합니다.
     *
     * @param code    에러 코드 (예: AUTH_001)
     * @param message 에러 메시지
     * @param <T>     응답 데이터 타입
     * @return 에러 응답
     */
    public static <T> ApiResponse<T> error(final String code, final String message) {
        return new ApiResponse<>(code, message, null, null);
    }

    /**
     * 유효성 검증 에러 응답을 생성합니다.
     *
     * @param code    에러 코드
     * @param message 에러 메시지
     * @param errors  필드 에러 목록
     * @param <T>     응답 데이터 타입
     * @return 유효성 검증 에러 응답
     */
    public static <T> ApiResponse<T> validationError(
            final String code,
            final String message,
            final List<FieldError> errors) {
        return new ApiResponse<>(code, message, null, errors);
    }
}
