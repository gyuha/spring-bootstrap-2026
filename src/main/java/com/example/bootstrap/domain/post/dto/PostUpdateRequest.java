package com.example.bootstrap.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시글 수정 요청 (BOARD-04). 소유권(작성자-or-ADMIN)은 서비스 레이어에서 강제한다(D-75).
 *
 * <p>{@code title}은 필수·최대 200자, {@code content}는 필수. 위반 시 기본 400(D-83).
 */
public record PostUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content) {
}
