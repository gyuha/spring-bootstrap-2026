package com.example.bootstrap.domain.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.bootstrap.domain.comment.entity.Comment;
import com.example.bootstrap.domain.comment.mapper.CommentQueryMapper;
import com.example.bootstrap.domain.comment.repository.CommentRepository;
import com.example.bootstrap.domain.comment.service.CommentService;
import com.example.bootstrap.domain.post.entity.Post;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * comment 작성/대댓글/삭제 + 소유권 분기 단위 검증 (CMNT-01/02/03/04, D-75).
 *
 * <p>Mockito로 협력자를 mock해 소유권 로직(author_id == userId || isAdmin)과 1단계 대댓글 강제만
 * 박제한다. 실 DB·MyBatis 집계·삭제 부모 post SQL 제외·N+1 부재는 04-05 통합 테스트가 담당한다.
 * 핵심 박제:
 * <ul>
 *   <li>create: post 존재(deleted_at IS NULL) → 루트 댓글 저장, author=현재 userId.
 *   <li>create reply: 부모 댓글의 parent_comment_id가 NULL(=루트)일 때만 허용. 부모가 이미
 *       대댓글이면(parent != null) → {@link ErrorCode#FORBIDDEN_OPERATION}(대댓글의 대댓글 금지, CMNT-02).
 *   <li>삭제 post에 작성 시도 → {@link ErrorCode#POST_NOT_FOUND}(D-75b — @SQLRestriction이 삭제 post 제외).
 *   <li>delete: 작성자 본인/ADMIN 성공(soft delete), 타인 → {@link ErrorCode#FORBIDDEN_OPERATION},
 *       미존재/삭제 → {@link ErrorCode#COMMENT_NOT_FOUND}.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    CommentRepository commentRepository;

    @Mock
    PostRepository postRepository;

    @Mock
    CommentQueryMapper commentQueryMapper;

    private CommentService service() {
        return new CommentService(commentRepository, postRepository, commentQueryMapper);
    }

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID POST = UUID.randomUUID();
    private static final UUID BOARD = UUID.randomUUID();

    private Post existingPost() {
        return Post.create(BOARD, AUTHOR, "글제목", "글내용");
    }

    private Comment rootComment() {
        return Comment.create(POST, AUTHOR, null, "원댓글");
    }

    private Comment replyComment() {
        return Comment.create(POST, AUTHOR, UUID.randomUUID(), "원대댓글");
    }

    @Test
    void create_rootComment_setsAuthorFromCurrentUser_andPostMustExist() {
        when(postRepository.findById(POST)).thenReturn(Optional.of(existingPost()));
        when(commentRepository.save(ArgumentMatchers.any(Comment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = service().create(POST, AUTHOR, null, "댓글내용");

        assertThat(response.authorId()).isEqualTo(AUTHOR);
        assertThat(response.parentCommentId()).isNull();
        assertThat(response.content()).isEqualTo("댓글내용");
    }

    @Test
    void create_onDeletedPost_throwsPostNotFound() {
        // @SQLRestriction이 삭제 post를 findById에서 제외하므로 empty로 박제(D-75b).
        when(postRepository.findById(POST)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(POST, AUTHOR, null, "댓글"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void create_replyToRootComment_succeeds() {
        UUID parentId = UUID.randomUUID();
        when(postRepository.findById(POST)).thenReturn(Optional.of(existingPost()));
        when(commentRepository.findById(parentId)).thenReturn(Optional.of(rootComment()));
        when(commentRepository.save(ArgumentMatchers.any(Comment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = service().create(POST, AUTHOR, parentId, "대댓글");

        assertThat(response.parentCommentId()).isEqualTo(parentId);
    }

    @Test
    void create_replyToReply_throwsForbidden() {
        // 부모가 이미 대댓글(parent_comment_id != null) → 대댓글의 대댓글 금지(CMNT-02).
        UUID parentId = UUID.randomUUID();
        when(postRepository.findById(POST)).thenReturn(Optional.of(existingPost()));
        when(commentRepository.findById(parentId)).thenReturn(Optional.of(replyComment()));

        assertThatThrownBy(() -> service().create(POST, AUTHOR, parentId, "대대댓글"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN_OPERATION);
    }

    @Test
    void create_replyToMissingParent_throwsCommentNotFound() {
        UUID parentId = UUID.randomUUID();
        when(postRepository.findById(POST)).thenReturn(Optional.of(existingPost()));
        when(commentRepository.findById(parentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(POST, AUTHOR, parentId, "대댓글"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }

    @Test
    void delete_byAuthor_softDeletes() {
        UUID commentId = UUID.randomUUID();
        Comment comment = rootComment();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        service().delete(commentId, AUTHOR, false);

        assertThat(comment.isDeleted()).isTrue();
    }

    @Test
    void delete_byAdmin_softDeletesEvenIfNotAuthor() {
        UUID commentId = UUID.randomUUID();
        Comment comment = rootComment();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        service().delete(commentId, OTHER, true);

        assertThat(comment.isDeleted()).isTrue();
    }

    @Test
    void delete_byOtherNonAdmin_throwsForbidden() {
        UUID commentId = UUID.randomUUID();
        Comment comment = rootComment();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service().delete(commentId, OTHER, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN_OPERATION);
        assertThat(comment.isDeleted()).isFalse();
    }

    @Test
    void delete_missingComment_throwsCommentNotFound() {
        UUID commentId = UUID.randomUUID();
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());
        lenient().when(postRepository.findById(ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(commentId, AUTHOR, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.COMMENT_NOT_FOUND);
    }
}
