package com.example.bootstrap.global.response;

import java.util.List;

/**
 * 페이지네이션 응답 클래스.
 *
 * <p>페이지네이션된 데이터 목록과 메타정보를 포함합니다.
 *
 * @param <T> 콘텐츠 타입
 */
public record PageResponse<T>(
    List<T> content,
    long totalCount,
    int totalPages,
    int page,
    int size
) {

    /**
     * 방어적 복사 compact constructor.
     *
     * <p>외부에서 전달된 가변 컬렉션을 불변 복사본으로 저장합니다.
     *
     * @param content    콘텐츠 목록
     * @param totalCount 전체 항목 수
     * @param totalPages 전체 페이지 수
     * @param page       현재 페이지 번호
     * @param size       페이지 크기
     */
    public PageResponse {
        content = content != null ? List.copyOf(content) : List.of();
    }

    /**
     * 페이지 응답을 생성합니다.
     *
     * @param content    페이지 데이터 목록
     * @param totalCount 전체 항목 수
     * @param page       현재 페이지 번호 (0부터 시작)
     * @param size       페이지 크기
     * @param <T>        콘텐츠 타입
     * @return 페이지 응답
     */
    public static <T> PageResponse<T> of(
            final List<T> content,
            final long totalCount,
            final int page,
            final int size) {
        final int totalPages = size > 0 ? (int) Math.ceil((double) totalCount / size) : 0;
        return new PageResponse<>(content, totalCount, totalPages, page, size);
    }
}
