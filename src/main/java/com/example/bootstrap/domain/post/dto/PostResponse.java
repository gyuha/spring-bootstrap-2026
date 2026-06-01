package com.example.bootstrap.domain.post.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 게시글 단건 응답 DTO (BOARD-02/03). 작성/조회 응답에 공용 사용한다. 작성 응답은 저장한 JPA 엔티티
 * getter로 직접 구성한다(P2/D-71 — 재조회 회피).
 *
 * @param createdAt 생성 시각 (UTC Instant, D-13)
 */
public record PostResponse(
        UUID id, UUID boardId, UUID authorId, String title, String content, Instant createdAt) {
}
