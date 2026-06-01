package com.example.bootstrap.domain.board.service;

import com.example.bootstrap.domain.board.dto.BoardResponse;
import com.example.bootstrap.domain.board.entity.Board;
import com.example.bootstrap.domain.board.repository.BoardRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 비즈니스 로직 + 트랜잭션 경계 (BOARD-01, D-08).
 *
 * <p>생성은 ADMIN 전용이며 인가는 컨트롤러의 {@code @PreAuthorize("hasRole('ADMIN')")}가 강제한다
 * (D-75/D-80 — SecurityConfig 경로 매처 무변경). 생성 응답은 저장한 엔티티 getter로 직접 구성해
 * 같은 트랜잭션 내 재조회를 회피한다(P2/D-71). 단건 조회는 JPA {@code findById}이며
 * BaseEntity {@code @SQLRestriction}이 soft-delete를 자동 제외한다(D-50).
 */
@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    @Transactional
    public BoardResponse create(String name) {
        Board saved = boardRepository.save(Board.create(name));
        return new BoardResponse(saved.getId(), saved.getName(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public BoardResponse findOne(UUID boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        return new BoardResponse(board.getId(), board.getName(), board.getCreatedAt());
    }
}
