package com.example.bootstrap.domain.post.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 게시글 목록 평탄 DTO + 리액션 카운트 (BOARD-03, QUERY-01, D-72).
 *
 * <p>MyBatis가 컬럼 alias(record 컴포넌트명)로 생성자 auto-mapping한다(UserQueryMapper 패턴).
 * {@code likeCount}/{@code dislikeCount}는 목록 SQL의 단일 {@code LEFT JOIN reactions ... GROUP BY}
 * + {@code COUNT(*) FILTER (...)}로 산출되며 목록 크기와 무관하게 실행 SQL이 상수다(N+1 금지, D-72/P9).
 *
 * @param createdAt 생성 시각 (UTC Instant, D-13)
 */
public record PostListItem(
        UUID id,
        String title,
        UUID authorId,
        Instant createdAt,
        long likeCount,
        long dislikeCount) {
}
