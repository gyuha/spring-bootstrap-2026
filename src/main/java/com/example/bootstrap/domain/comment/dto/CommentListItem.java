package com.example.bootstrap.domain.comment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 댓글 목록 평탄 DTO + 리액션 카운트 (CMNT-03, QUERY-01, D-72).
 *
 * <p>MyBatis가 컬럼 alias(record 컴포넌트명)로 생성자 auto-mapping한다(UserQueryMapper/PostListItem
 * 패턴). {@code likeCount}/{@code dislikeCount}는 목록 SQL의 단일 {@code LEFT JOIN reactions ... GROUP BY}
 * + {@code COUNT(*) FILTER (...)}로 산출되며 목록 크기와 무관하게 실행 SQL이 상수다(N+1 금지, D-72/P9).
 *
 * @param createdAt 생성 시각 (UTC Instant, D-13)
 */
public record CommentListItem(
        UUID id,
        UUID parentCommentId,
        UUID authorId,
        String content,
        Instant createdAt,
        long likeCount,
        long dislikeCount) {
}
