package com.example.bootstrap.domain.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.example.bootstrap.domain.board.entity.Board;
import com.example.bootstrap.domain.board.repository.BoardRepository;
import com.example.bootstrap.domain.post.entity.Post;
import com.example.bootstrap.domain.post.mapper.PostQueryMapper;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.domain.post.service.PostService;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * post CRUD + 소유권 분기 단위 검증 (BOARD-02/03/04/05, D-75).
 *
 * <p>Mockito로 협력자를 mock해 소유권 로직(author_id == userId || isAdmin)만 박제한다. 실 DB·MyBatis
 * 집계·N+1 부재는 04-05 통합 테스트가 담당한다. 핵심 박제: 작성자 본인 update/delete 성공, 타인
 * (다른 userId, non-admin) → {@link ErrorCode#FORBIDDEN_OPERATION}, ADMIN(isAdmin=true)은 타인 글도
 * 성공, 미존재/삭제 post → {@link ErrorCode#POST_NOT_FOUND}.
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    PostRepository postRepository;

    @Mock
    BoardRepository boardRepository;

    @Mock
    PostQueryMapper postQueryMapper;

    private PostService service() {
        return new PostService(postRepository, boardRepository, postQueryMapper);
    }

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID BOARD = UUID.randomUUID();

    private Post existingPost() {
        return Post.create(BOARD, AUTHOR, "원제목", "원내용");
    }

    @Test
    void create_setsAuthorFromCurrentUser_andBoardMustExist() {
        Board board = Board.create("자유게시판");
        when(boardRepository.findById(BOARD)).thenReturn(Optional.of(board));
        when(postRepository.save(org.mockito.ArgumentMatchers.any(Post.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var response = service().create(BOARD, AUTHOR, "제목", "내용");

        assertThat(response.authorId()).isEqualTo(AUTHOR);
        assertThat(response.title()).isEqualTo("제목");
    }

    @Test
    void create_missingBoard_throwsBoardNotFound() {
        when(boardRepository.findById(BOARD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(BOARD, AUTHOR, "제목", "내용"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOARD_NOT_FOUND);
    }

    @Test
    void update_byAuthor_succeeds() {
        UUID postId = UUID.randomUUID();
        Post post = existingPost();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        service().update(postId, AUTHOR, false, "새제목", "새내용");

        assertThat(post.getTitle()).isEqualTo("새제목");
        assertThat(post.getContent()).isEqualTo("새내용");
    }

    @Test
    void update_byAdmin_succeedsEvenIfNotAuthor() {
        UUID postId = UUID.randomUUID();
        Post post = existingPost();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        service().update(postId, OTHER, true, "관리자수정", "관리자내용");

        assertThat(post.getTitle()).isEqualTo("관리자수정");
    }

    @Test
    void update_byOtherNonAdmin_throwsForbidden() {
        UUID postId = UUID.randomUUID();
        Post post = existingPost();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service().update(postId, OTHER, false, "x", "y"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN_OPERATION);
        assertThat(post.getTitle()).isEqualTo("원제목");
    }

    @Test
    void update_missingPost_throwsPostNotFound() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().update(postId, AUTHOR, false, "x", "y"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }

    @Test
    void delete_byAuthor_softDeletes() {
        UUID postId = UUID.randomUUID();
        Post post = existingPost();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        service().delete(postId, AUTHOR, false);

        assertThat(post.isDeleted()).isTrue();
    }

    @Test
    void delete_byAdmin_softDeletesEvenIfNotAuthor() {
        UUID postId = UUID.randomUUID();
        Post post = existingPost();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        service().delete(postId, OTHER, true);

        assertThat(post.isDeleted()).isTrue();
    }

    @Test
    void delete_byOtherNonAdmin_throwsForbidden() {
        UUID postId = UUID.randomUUID();
        Post post = existingPost();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service().delete(postId, OTHER, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN_OPERATION);
        assertThat(post.isDeleted()).isFalse();
    }

    @Test
    void delete_missingPost_throwsPostNotFound() {
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        lenient().when(boardRepository.findById(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(postId, AUTHOR, false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.POST_NOT_FOUND);
    }
}
