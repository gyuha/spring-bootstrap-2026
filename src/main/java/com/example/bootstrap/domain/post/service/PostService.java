package com.example.bootstrap.domain.post.service;

import com.example.bootstrap.domain.board.repository.BoardRepository;
import com.example.bootstrap.domain.post.dto.PostListItem;
import com.example.bootstrap.domain.post.dto.PostResponse;
import com.example.bootstrap.domain.post.entity.Post;
import com.example.bootstrap.domain.post.mapper.PostQueryMapper;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시글 비즈니스 로직 + 트랜잭션 경계 (BOARD-02~05, D-08).
 *
 * <p>쓰기/단건 CRUD는 JPA {@link PostRepository}, 목록+리액션 카운트 집계는 MyBatis
 * {@link PostQueryMapper}로 처리한다(D-72). 작성 응답은 저장 엔티티 getter로 직접 구성해 같은
 * 트랜잭션 내 MyBatis 재조회를 회피한다(P2/D-71).
 *
 * <p>수정/삭제 소유권: 경로/메서드 인가만으로는 작성자-or-ADMIN 의미론을 표현할 수 없으므로
 * 서비스에서 {@code author_id == userId || isAdmin}을 체크하고 아니면
 * {@link ErrorCode#FORBIDDEN_OPERATION}(403)을 던진다(D-75, T-04-03 IDOR 완화). author_id는 요청
 * 본문이 아닌 JWT subject(userId)에서만 설정한다(T-04-07 mass-assignment 방지).
 */
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final BoardRepository boardRepository;
    private final PostQueryMapper postQueryMapper;

    @Transactional
    public PostResponse create(UUID boardId, UUID authorId, String title, String content) {
        // board 존재(soft-delete 제외 — BaseEntity @SQLRestriction) 확인 후 작성한다(D-75b).
        boardRepository.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        Post saved = postRepository.save(Post.create(boardId, authorId, title, content));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PostResponse findOne(UUID postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        return toResponse(post);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostListItem> findPage(UUID boardId, int page, int size) {
        long offset = (long) page * size;
        return PageResponse.of(
                postQueryMapper.findPage(boardId, offset, size),
                postQueryMapper.countByBoard(boardId),
                page,
                size);
    }

    @Transactional
    public PostResponse update(
            UUID postId, UUID userId, boolean isAdmin, String title, String content) {
        Post post = requireOwned(postId, userId, isAdmin);
        post.update(title, content);
        return toResponse(post);
    }

    @Transactional
    public void delete(UUID postId, UUID userId, boolean isAdmin) {
        Post post = requireOwned(postId, userId, isAdmin);
        post.softDelete();
    }

    /** post 존재(POST_NOT_FOUND) + 소유권(작성자-or-ADMIN, 아니면 FORBIDDEN_OPERATION) 체크(D-75). */
    private Post requireOwned(UUID postId, UUID userId, boolean isAdmin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (!isAdmin && !post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_OPERATION);
        }
        return post;
    }

    private static PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getBoardId(),
                post.getAuthorId(),
                post.getTitle(),
                post.getContent(),
                post.getCreatedAt());
    }
}
