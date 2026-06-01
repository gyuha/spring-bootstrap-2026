package com.example.bootstrap.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 미인증 접근 → 401 ProblemDetail (D-38).
 *
 * <p>Security 필터 단계 예외는 {@code @RestControllerAdvice} 밖이므로, 토큰 부재/서명 실패/블랙리스트
 * 무효화 등 인증 실패를 여기서 RFC 9457 {@code application/problem+json}으로 변환한다.
 * {@link com.example.bootstrap.global.exception.GlobalExceptionHandler}와 동일하게 title/errorCode를
 * 채운다.
 */
@Component
public class ProblemDetailAuthEntryPoint implements AuthenticationEntryPoint {

    private static final String ERROR_CODE = "UNAUTHENTICATED";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ProblemDetailWriter.write(response, HttpStatus.UNAUTHORIZED, ERROR_CODE, "인증이 필요합니다");
    }
}
