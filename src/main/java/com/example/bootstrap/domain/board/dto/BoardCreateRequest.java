package com.example.bootstrap.domain.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 게시판 생성 요청 (BOARD-01). ADMIN 전용 경로에서 사용한다.
 *
 * <p>{@code name}은 필수이며 최대 100자(boards.name VARCHAR(100) 정합). 위반 시 Bean Validation이
 * {@code MethodArgumentNotValidException} → 기본 400 ProblemDetail로 처리된다(D-83).
 */
public record BoardCreateRequest(
        @NotBlank @Size(max = 100) String name) {
}
