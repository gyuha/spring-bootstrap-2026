package com.example.bootstrap.global.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 전역 예외 → RFC 9457 ProblemDetail 변환 (FOUND-04, D-18).
 *
 * <p>{@code spring.mvc.problemdetails.enabled=true}로 Spring MVC 내장 예외는 자동 변환되고,
 * 커스텀 예외는 여기서 처리한다. 500 fallback은 스택트레이스를 클라이언트에 노출하지 않고
 * 서버 로그에만 남긴다(Rule 2 — 정보 노출 방지).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.getStatus(), ex.getMessage());
        problem.setTitle(errorCode.name());
        problem.setProperty("errorCode", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus()).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, errorCode.getMessage());
        problem.setTitle(errorCode.name());
        problem.setProperty("errorCode", errorCode.name());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
