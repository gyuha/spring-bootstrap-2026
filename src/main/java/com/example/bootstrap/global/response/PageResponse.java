package com.example.bootstrap.global.response;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 표준 페이징 응답 (D-18).
 *
 * @param pageNumber 0-based 페이지 번호
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize,
        boolean hasNext,
        boolean hasPrevious) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize(),
                page.hasNext(),
                page.hasPrevious());
    }

    /**
     * MyBatis offset/limit 조회 경로용 팩토리 (D-48). {@code pageNumber}는 0-based.
     *
     * <p>{@code totalPages=ceil(total/size)}, {@code hasNext=(page+1)*size<total},
     * {@code hasPrevious=page>0}로 계산한다. {@code size<=0}이면 0 나눗셈을 막기 위해
     * {@code totalPages=0}, 네비게이션 false로 방어한다.
     */
    public static <T> PageResponse<T> of(
            List<T> content, long totalElements, int pageNumber, int pageSize) {
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        boolean hasNext = pageSize > 0 && (long) (pageNumber + 1) * pageSize < totalElements;
        boolean hasPrevious = pageNumber > 0;
        return new PageResponse<>(
                content, totalElements, totalPages, pageNumber, pageSize, hasNext, hasPrevious);
    }
}
