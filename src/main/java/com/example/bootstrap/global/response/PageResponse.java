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
}
