package com.example.bootstrap.domain.board.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 게시판 응답 DTO (BOARD-01). 저장한 JPA 엔티티 getter로 직접 구성한다(P2/D-71 — 재조회 회피).
 *
 * @param createdAt 생성 시각 (UTC Instant, D-13)
 */
public record BoardResponse(UUID id, String name, Instant createdAt) {
}
