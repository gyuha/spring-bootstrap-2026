package com.example.bootstrap.global.response;

/**
 * 표준 성공 응답 래퍼 (D-18).
 *
 * @param data 응답 페이로드. noContent의 경우 {@code null}.
 */
public record ApiResponse<T>(T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }

    public static <T> ApiResponse<T> noContent() {
        return new ApiResponse<>(null);
    }
}
