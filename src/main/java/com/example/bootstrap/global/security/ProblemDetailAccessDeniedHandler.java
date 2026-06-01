package com.example.bootstrap.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 인증됐으나 권한 부족 → 403 ProblemDetail (D-38).
 *
 * <p>인가 실패도 필터 단계 예외이므로 advice 밖이다. 401(미인증)과 분리해 403으로 응답한다.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final String ERROR_CODE = "ACCESS_DENIED";

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        ProblemDetailWriter.write(response, HttpStatus.FORBIDDEN, ERROR_CODE, "접근 권한이 없습니다");
    }
}
