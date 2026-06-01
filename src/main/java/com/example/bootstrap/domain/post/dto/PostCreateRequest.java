package com.example.bootstrap.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 작성 요청 (BOARD-02). 작성자(author_id)는 본문이 아닌 JWT subject에서만 설정한다(T-04-07
 * mass-assignment 방지) — 따라서 요청에 authorId 필드가 없다.
 *
 * <p>{@code title}은 필수·최대 200자(posts.title VARCHAR(200) 정합), {@code content}는 필수. 위반 시
 * Bean Validation → 기본 400 ProblemDetail(D-83).
 */
public record PostCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content) {
}
