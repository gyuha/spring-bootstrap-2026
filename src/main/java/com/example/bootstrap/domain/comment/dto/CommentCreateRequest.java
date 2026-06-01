package com.example.bootstrap.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 댓글/대댓글 작성 요청 (CMNT-01/02). 작성자(author_id)는 본문이 아닌 JWT subject에서만 설정한다
 * (T-04-11 mass-assignment 방지) — 따라서 요청에 authorId 필드가 없다.
 *
 * <p>{@code parentCommentId}가 null이면 루트 댓글, 값이 있으면 1단계 대댓글이다(CMNT-02). 부모가
 * 이미 대댓글이면 서비스에서 거부한다(2단계 이상 금지). {@code content}는 필수·최대 1000자. 위반 시
 * Bean Validation → 기본 400 ProblemDetail(D-83).
 */
public record CommentCreateRequest(
        @NotBlank @Size(max = 1000) String content,
        UUID parentCommentId) {
}
