package com.example.bootstrap.domain.comment.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 댓글 단건 응답 DTO (CMNT-01/02). 작성 응답에 사용한다. 작성 응답은 저장한 JPA 엔티티 getter로
 * 직접 구성한다(P2/D-71 — 재조회 회피).
 *
 * <p>{@code parentCommentId}가 null이면 루트 댓글, 값이 있으면 1단계 대댓글이다.
 *
 * @param createdAt 생성 시각 (UTC Instant, D-13)
 */
public record CommentResponse(
        UUID id,
        UUID postId,
        UUID parentCommentId,
        UUID authorId,
        String content,
        Instant createdAt) {
}
