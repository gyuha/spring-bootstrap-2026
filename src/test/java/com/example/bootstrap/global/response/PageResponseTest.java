package com.example.bootstrap.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link PageResponse#of(List, long, int, int)} MyBatis offset/limit 경로 팩토리 단위 검증 (D-48).
 *
 * <p>totalPages=ceil(total/size), hasNext=(page+1)*size<total, hasPrevious=page>0 계산과
 * total=0/size<=0 가드를 박제한다.
 */
class PageResponseTest {

    @Test
    void of_firstPage_computesNextWithoutPrevious() {
        PageResponse<String> page = PageResponse.of(List.of("a", "b"), 5, 0, 2);

        assertThat(page.content()).containsExactly("a", "b");
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.pageNumber()).isZero();
        assertThat(page.pageSize()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    void of_lastPage_computesPreviousWithoutNext() {
        PageResponse<String> page = PageResponse.of(List.of("e"), 5, 2, 2);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void of_emptyTotal_yieldsZeroPagesAndNoNavigation() {
        PageResponse<String> page = PageResponse.of(List.of(), 0, 0, 20);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    void of_nonPositiveSize_guardsAgainstDivisionByZero() {
        PageResponse<String> page = PageResponse.of(List.of(), 3, 0, 0);

        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
    }
}
