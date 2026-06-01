package com.example.bootstrap.global.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
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

    /**
     * 메서드 보안({@code @PreAuthorize}) 인가 실패 → 403 ProblemDetail (D-38).
     *
     * <p>{@code @PreAuthorize} 거부는 컨트롤러 호출 단계에서 {@link AuthorizationDeniedException}을
     * 던지므로 {@code ExceptionTranslationFilter}가 아닌 advice로 들어온다. catch-all
     * {@code Exception} 핸들러가 500으로 삼키지 않도록 명시 매핑해, 필터 단계
     * {@link com.example.bootstrap.global.security.ProblemDetailAccessDeniedHandler}와 동일하게
     * 403/{@code ACCESS_DENIED}로 응답한다(Rule 1 — 인가 실패가 500으로 노출되던 버그).
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "접근 권한이 없습니다");
        problem.setTitle("ACCESS_DENIED");
        problem.setProperty("errorCode", "ACCESS_DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problem);
    }

    /**
     * {@code @Validated} 메서드 파라미터 제약 위반({@code @RequestParam} page/size 등) → 400 ProblemDetail.
     *
     * <p>클래스 레벨 {@code @Validated} + 파라미터 제약은 {@link ConstraintViolationException}(jakarta)을
     * 던지며, 이는 {@code ResponseEntityExceptionHandler}의 기본 처리 대상이 아니라 catch-all
     * {@code Exception} 핸들러가 500으로 삼킨다. 명시 매핑해 400으로 응답한다(리뷰 I1 — 음수/과대
     * page·size가 500으로 노출되던 버그).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("INVALID_PARAMETER");
        problem.setProperty("errorCode", "INVALID_PARAMETER");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
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
