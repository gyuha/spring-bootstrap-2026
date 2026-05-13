package com.example.bootstrap.global.exception;

import com.example.bootstrap.global.response.ApiResponse;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;

/**
 * 전역 예외 처리 핸들러.
 *
 * <p>모든 예외를 {@link ApiResponse} Envelope 형식으로 변환합니다.
 * Accept-Language 헤더를 {@link LocaleContextHolder}에 반영한 후
 * {@link MessageSource}를 통해 locale 기반 메시지를 동적으로 반환합니다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    /**
     * 생성자 주입.
     *
     * @param messageSource i18n 메시지 소스
     */
    public GlobalExceptionHandler(final MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 비즈니스 예외를 처리합니다.
     *
     * @param ex       {@link BusinessException}
     * @param exchange {@link ServerWebExchange}
     * @return 에러 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            final BusinessException ex,
            final ServerWebExchange exchange) {
        final ErrorCode errorCode = ex.getErrorCode();
        final Locale locale = resolveLocale(exchange);
        try {
            final String message = messageSource.getMessage(
                errorCode.getCode(), null, ex.getMessage(), locale);
            return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ApiResponse.error(errorCode.getCode(), message));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /**
     * 유효성 검증 예외를 처리합니다.
     *
     * @param ex       {@link WebExchangeBindException}
     * @param exchange {@link ServerWebExchange}
     * @return 유효성 검증 에러 응답
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            final WebExchangeBindException ex,
            final ServerWebExchange exchange) {
        final Locale locale = resolveLocale(exchange);
        try {
            final List<ApiResponse.FieldError> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ApiResponse.FieldError(
                    fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
            final String message = messageSource.getMessage(
                "COMMON_001", null, "입력값이 유효하지 않습니다.", locale);
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.validationError("COMMON_001", message, errors));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /**
     * 예상치 못한 예외를 처리합니다.
     *
     * @param ex       {@link Exception}
     * @param exchange {@link ServerWebExchange}
     * @return 서버 에러 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            final Exception ex,
            final ServerWebExchange exchange) {
        final Locale locale = resolveLocale(exchange);
        try {
            final String message = messageSource.getMessage(
                "COMMON_002", null, "서버 내부 오류가 발생했습니다.", locale);
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("COMMON_002", message));
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
    }

    /**
     * ServerWebExchange에서 Accept-Language를 추출하여 {@link LocaleContextHolder}에 설정합니다.
     *
     * <p>exchange의 {@link LocaleContext}를 {@code LocaleContextHolder}에 바인딩하여
     * 요청 처리 스레드에서 {@link MessageSource}가 올바른 locale로 메시지를 조회할 수 있도록 합니다.
     * 핸들러 완료 후 반드시 {@link LocaleContextHolder#resetLocaleContext()}를 호출해
     * 스레드 오염(thread pollution)을 방지해야 합니다.
     *
     * @param exchange {@link ServerWebExchange}
     * @return 요청 Locale (기본값: 한국어)
     */
    private Locale resolveLocale(final ServerWebExchange exchange) {
        final LocaleContext localeContext = exchange.getLocaleContext();
        LocaleContextHolder.setLocaleContext(localeContext, true);
        return Optional.ofNullable(localeContext.getLocale())
            .orElse(Locale.KOREAN);
    }
}
