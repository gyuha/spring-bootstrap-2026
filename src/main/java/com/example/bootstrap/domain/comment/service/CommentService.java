package com.example.bootstrap.domain.comment.service;

import com.example.bootstrap.domain.comment.dto.CommentListItem;
import com.example.bootstrap.domain.comment.dto.CommentResponse;
import com.example.bootstrap.domain.comment.entity.Comment;
import com.example.bootstrap.domain.comment.mapper.CommentQueryMapper;
import com.example.bootstrap.domain.comment.repository.CommentRepository;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 비즈니스 로직 + 트랜잭션 경계 (CMNT-01~04, D-08).
 *
 * <p>쓰기/단건 CRUD는 JPA {@link CommentRepository}, 목록+리액션 카운트 집계는 MyBatis
 * {@link CommentQueryMapper}로 처리한다(D-72). 작성 응답은 저장 엔티티 getter로 직접 구성해 같은
 * 트랜잭션 내 MyBatis 재조회를 회피한다(P2/D-71).
 *
 * <p>1단계 대댓글 강제(CMNT-02, T-04-09): {@code parentCommentId}가 주어지면 부모 댓글을 조회해
 * 그 {@code parentCommentId}가 null(=루트)인지 검증한다. 부모가 이미 대댓글이면(non-null)
 * {@link ErrorCode#FORBIDDEN_OPERATION}을 던진다 — 재귀 깊이 검사 없이 한 단계만 확인하면 충분하다.
 *
 * <p>삭제 소유권(CMNT-04, T-04-08 IDOR 완화): 경로/메서드 인가만으로는 작성자-or-ADMIN 의미론을
 * 표현할 수 없으므로 서비스에서 {@code author_id == userId || isAdmin}을 체크하고 아니면
 * {@link ErrorCode#FORBIDDEN_OPERATION}(403)을 던진다(D-75). author_id는 요청 본문이 아닌
 * JWT subject(userId)에서만 설정한다(T-04-11 mass-assignment 방지). 삭제 post에 작성 시도는
 * {@code @SQLRestriction}이 삭제 post를 findById에서 제외하므로 {@link ErrorCode#POST_NOT_FOUND}이다(D-75b).
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final CommentQueryMapper commentQueryMapper;

    @Transactional
    public CommentResponse create(
            UUID postId, UUID authorId, UUID parentCommentId, String content) {
        // post 존재(soft-delete 제외 — BaseEntity @SQLRestriction) 확인 후 작성한다(D-75b).
        postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
        if (parentCommentId != null) {
            Comment parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            // 1단계 강제: 부모가 이미 대댓글이면 대댓글의 대댓글 금지(CMNT-02, T-04-09).
            if (parent.getParentCommentId() != null) {
                throw new BusinessException(ErrorCode.FORBIDDEN_OPERATION);
            }
        }
        Comment saved =
                commentRepository.save(Comment.create(postId, authorId, parentCommentId, content));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentListItem> findPage(UUID postId, int page, int size) {
        long offset = (long) page * size;
        return PageResponse.of(
                commentQueryMapper.findPage(postId, offset, size),
                commentQueryMapper.countByPost(postId),
                page,
                size);
    }

    @Transactional
    public void delete(UUID commentId, UUID userId, boolean isAdmin) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!isAdmin && !comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN_OPERATION);
        }
        comment.softDelete();
    }

    private static CommentResponse toResponse(Comment comment) {
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getParentCommentId(),
                comment.getAuthorId(),
                comment.getContent(),
                comment.getCreatedAt());
    }
}
