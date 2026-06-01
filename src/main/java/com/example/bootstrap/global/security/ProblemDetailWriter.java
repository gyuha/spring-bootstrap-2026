package com.example.bootstrap.global.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Security 필터 단계 예외(미인증/권한부족)를 RFC 9457 ProblemDetail JSON으로 응답에 쓴다 (D-38).
 *
 * <p>필터 단계 예외는 {@code @RestControllerAdvice} 밖이라 {@code GlobalExceptionHandler}를 거치지
 * 않는다. ObjectMapper 빈 주입(SB4의 Jackson 2/3 이중 클래스패스 모호성)을 피하기 위해 고정 형태의
 * ProblemDetail을 직접 직렬화한다. {@code GlobalExceptionHandler}와 동일하게 title/errorCode를 담는다.
 */
final class ProblemDetailWriter {

    private ProblemDetailWriter() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String errorCode, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String body = "{\"type\":\"about:blank\""
                + ",\"title\":\"" + escape(errorCode) + "\""
                + ",\"status\":" + status.value()
                + ",\"detail\":\"" + escape(detail) + "\""
                + ",\"errorCode\":\"" + escape(errorCode) + "\"}";
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
