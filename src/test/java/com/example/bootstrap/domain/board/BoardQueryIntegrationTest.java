package com.example.bootstrap.domain.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bootstrap.domain.board.entity.Board;
import com.example.bootstrap.domain.board.repository.BoardRepository;
import com.example.bootstrap.domain.comment.dto.CommentListItem;
import com.example.bootstrap.domain.comment.entity.Comment;
import com.example.bootstrap.domain.comment.repository.CommentRepository;
import com.example.bootstrap.domain.comment.service.CommentService;
import com.example.bootstrap.domain.post.dto.PostListItem;
import com.example.bootstrap.domain.post.entity.Post;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.domain.post.service.PostService;
import com.example.bootstrap.domain.reaction.entity.ReactionTarget;
import com.example.bootstrap.domain.reaction.entity.ReactionType;
import com.example.bootstrap.domain.reaction.mapper.ReactionMapper;
import com.example.bootstrap.global.BaseIntegrationTest;
import com.example.bootstrap.global.response.PageResponse;
import com.example.bootstrap.support.StatementCountingInterceptor;
import com.example.bootstrap.support.StatementCountingTestConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MyBatis 목록/집계 N+1 부재 프로그램적 박제 + 삭제 제외·카운트 정확성 통합 테스트 (SC4, QUERY-01,
 * T-04-17) — mock 없는 실 PG17.
 *
 * <p><b>N+1 부재(코드 리뷰 escape 금지):</b> {@link StatementCountingInterceptor}로 목록 조회가 실행한
 * SQL 문 수를 잰다. post 1건 시드 후 목록 조회 → 실행 수 N1, post 5건 시드 후 같은 조회 → 실행 수 N2.
 * {@code N1 == N2}를 단언한다 — 목록 크기(1→5)와 무관하게 매퍼가 실행하는 SQL 문 수가 상수임을
 * 증명한다(단일 GROUP BY 집계면 항상 동일, 행별 상관 서브쿼리면 행 수만큼 증가해 실패). 댓글 목록도
 * 동형으로 1건/5건 실행 수 동일을 확인한다.
 *
 * <p>삭제 제외: soft-delete된 post/comment가 목록·카운트에서 빠지고(deleted_at IS NULL), 부모 post가
 * 삭제되면 그 댓글도 목록에서 제외됨(comments JOIN posts deleted_at IS NULL)을 단언한다.
 */
@Import(StatementCountingTestConfig.class)
class BoardQueryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    PostService postService;

    @Autowired
    CommentService commentService;

    @Autowired
    BoardRepository boardRepository;

    @Autowired
    PostRepository postRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    ReactionMapper reactionMapper;

    @Autowired
    StatementCountingInterceptor interceptor;

    @Autowired
    TransactionTemplate tx;

    /** post 목록: 1건/5건 실행 SQL 문 수 동일(N+1 부재) + 리액션 카운트 정확성. */
    @Test
    void postList_statementCountConstant_andReactionCountAccurate() {
        UUID board1 = seedBoard();
        UUID post1 = seedPost(board1);
        // 여러 user가 like/dislike → 집계 정확성
        seedReaction(ReactionTarget.POST, post1, ReactionType.LIKE);
        seedReaction(ReactionTarget.POST, post1, ReactionType.LIKE);
        seedReaction(ReactionTarget.POST, post1, ReactionType.DISLIKE);

        interceptor.reset();
        PageResponse<PostListItem> page1 = postService.findPage(board1, 0, 100);
        int n1 = interceptor.getCount();

        assertThat(page1.content()).hasSize(1);
        assertThat(page1.content().get(0).likeCount()).isEqualTo(2L);
        assertThat(page1.content().get(0).dislikeCount()).isEqualTo(1L);

        // 5건 시드한 별도 board로 같은 목록 조회 → 실행 수 동일해야 함
        UUID board5 = seedBoard();
        for (int i = 0; i < 5; i++) {
            UUID p = seedPost(board5);
            seedReaction(ReactionTarget.POST, p, ReactionType.LIKE);
        }

        interceptor.reset();
        PageResponse<PostListItem> page5 = postService.findPage(board5, 0, 100);
        int n2 = interceptor.getCount();

        assertThat(page5.content()).hasSize(5);
        assertThat(n2)
                .as("목록 크기 1→5에도 실행 SQL 문 수가 상수여야 한다(N+1 부재). n1=%d n2=%d", n1, n2)
                .isEqualTo(n1);
    }

    /** comment 목록: 1건/5건 실행 SQL 문 수 동일(N+1 부재). */
    @Test
    void commentList_statementCountConstant() {
        UUID board = seedBoard();
        UUID postA = seedPost(board);
        UUID c1 = seedComment(postA, null);
        seedReaction(ReactionTarget.COMMENT, c1, ReactionType.LIKE);

        interceptor.reset();
        PageResponse<CommentListItem> page1 = commentService.findPage(postA, 0, 100);
        int n1 = interceptor.getCount();
        assertThat(page1.content()).hasSize(1);

        UUID postB = seedPost(board);
        for (int i = 0; i < 5; i++) {
            UUID c = seedComment(postB, null);
            seedReaction(ReactionTarget.COMMENT, c, ReactionType.LIKE);
        }

        interceptor.reset();
        PageResponse<CommentListItem> page5 = commentService.findPage(postB, 0, 100);
        int n2 = interceptor.getCount();

        assertThat(page5.content()).hasSize(5);
        assertThat(n2)
                .as("댓글 목록 크기 1→5에도 실행 SQL 문 수가 상수여야 한다(N+1 부재). n1=%d n2=%d", n1, n2)
                .isEqualTo(n1);
    }

    /** 삭제 post가 목록·카운트에서 제외(deleted_at IS NULL). */
    @Test
    void deletedPost_excludedFromListAndCount() {
        UUID board = seedBoard();
        UUID keep = seedPost(board);
        UUID drop = seedPost(board);

        softDeletePost(drop);

        PageResponse<PostListItem> page = postService.findPage(board, 0, 100);
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content())
                .extracting(PostListItem::id)
                .containsExactly(keep)
                .doesNotContain(drop);
    }

    /** 삭제 comment + 삭제 부모 post가 댓글 목록·카운트에서 제외. */
    @Test
    void deletedComment_andDeletedParentPost_excludedFromCommentList() {
        UUID board = seedBoard();
        UUID post = seedPost(board);
        UUID keep = seedComment(post, null);
        UUID drop = seedComment(post, null);

        softDeleteComment(drop);

        PageResponse<CommentListItem> page = commentService.findPage(post, 0, 100);
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.content())
                .extracting(CommentListItem::id)
                .containsExactly(keep)
                .doesNotContain(drop);

        // 부모 post 삭제 → 남은 댓글도 목록에서 제외(comments JOIN posts deleted_at IS NULL)
        softDeletePost(post);
        PageResponse<CommentListItem> afterPostDelete = commentService.findPage(post, 0, 100);
        assertThat(afterPostDelete.totalElements()).isEqualTo(0L);
        assertThat(afterPostDelete.content()).isEmpty();
    }

    // --- seed helpers (각 작업을 커밋해 후속 조회가 볼 수 있게 한다) ---

    private UUID seedBoard() {
        return tx.execute(s -> boardRepository.save(Board.create("b-" + UUID.randomUUID())).getId());
    }

    private UUID seedPost(UUID boardId) {
        return tx.execute(s -> postRepository.save(
                        Post.create(boardId, UUID.randomUUID(), "title", "content"))
                .getId());
    }

    private UUID seedComment(UUID postId, UUID parentId) {
        return tx.execute(s -> commentRepository.save(
                        Comment.create(postId, UUID.randomUUID(), parentId, "content"))
                .getId());
    }

    private void seedReaction(ReactionTarget target, UUID targetId, ReactionType type) {
        tx.executeWithoutResult(s ->
                reactionMapper.upsert(target.name(), targetId, UUID.randomUUID(), type.name()));
    }

    private void softDeletePost(UUID postId) {
        tx.executeWithoutResult(s -> postRepository.findById(postId).orElseThrow().softDelete());
    }

    private void softDeleteComment(UUID commentId) {
        tx.executeWithoutResult(
                s -> commentRepository.findById(commentId).orElseThrow().softDelete());
    }
}
