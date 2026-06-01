package com.example.bootstrap.domain.reaction.dto;

import com.example.bootstrap.domain.reaction.entity.ReactionType;
import jakarta.validation.constraints.NotNull;

/**
 * 리액션 set 요청 (BOARD-06/CMNT-05, D-74).
 *
 * <p>{@code type}은 필수(LIKE/DISLIKE). 위반 시 Bean Validation → 기본 400 ProblemDetail(D-83).
 * user_id는 본문이 아닌 JWT subject(CurrentUser.userId)에서만 설정한다(T-04-14 mass-assignment 방지).
 */
public record ReactionSetRequest(@NotNull ReactionType type) {
}
