package com.example.bootstrap.domain.reaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bootstrap.domain.comment.entity.Comment;
import com.example.bootstrap.domain.comment.repository.CommentRepository;
import com.example.bootstrap.domain.post.entity.Post;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.domain.reaction.entity.ReactionTarget;
import com.example.bootstrap.domain.reaction.entity.ReactionType;
import com.example.bootstrap.domain.reaction.service.ReactionService;
import com.example.bootstrap.global.BaseIntegrationTest;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 리액션 set/remove 멱등성·type 전이·삭제 target 차단 통합 테스트 (BOARD-06/CMNT-05, D-73/D-74/D-75b).
 *
 * <p>멱등은 DB {@code ON CONFLICT DO UPDATE} 동작이라 mock이 부적합하므로 {@link BaseIntegrationTest}
 * 상속 실 PG로 upsert/DELETE 행 수와 type 전이를 박제한다. reaction은 JPA 엔티티가 없으므로 행 수
 * 확인은 {@link JdbcTemplate}로 직접 COUNT한다(EntityManager 경유 불가). 동시 2요청(2 스레드) 멱등
 * 통합 검증은 04-05의 MANDATORY 테스트가 담당하고, 여기서는 단일 호출 멱등(2회 set=1행)을 박제한다.
 */
class ReactionServiceTest extends BaseIntegrationTest {

    @Autowired
    private ReactionService reactionService;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long countRows(String targetType, UUID targetId, UUID userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reactions WHERE target_type = ? AND target_id = ? AND user_id = ?",
                Long.class,
                targetType,
                targetId,
                userId);
        return n == null ? 0L : n;
    }

    private String typeOf(UUID targetId, UUID userId) {
        return jdbcTemplate.queryForObject(
                "SELECT type FROM reactions WHERE target_id = ? AND user_id = ?",
                String.class,
                targetId,
                userId);
    }

    private UUID persistPost() {
        Post post = postRepository.save(
                Post.create(UUID.randomUUID(), UUID.randomUUID(), "title", "content"));
        return post.getId();
    }

    @Test
    void set_creates_single_row() {
        UUID postId = persistPost();
        UUID userId = UUID.randomUUID();

        reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE);

        assertThat(countRows("POST", postId, userId)).isEqualTo(1L);
        assertThat(typeOf(postId, userId)).isEqualTo("LIKE");
    }

    @Test
    void set_twice_is_idempotent_single_row() {
        UUID postId = persistPost();
        UUID userId = UUID.randomUUID();

        reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE);
        reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE);

        assertThat(countRows("POST", postId, userId)).isEqualTo(1L);
        assertThat(typeOf(postId, userId)).isEqualTo("LIKE");
    }

    @Test
    void like_then_dislike_updates_row_no_extra_row() {
        UUID postId = persistPost();
        UUID userId = UUID.randomUUID();

        reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE);
        reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.DISLIKE);

        assertThat(countRows("POST", postId, userId)).isEqualTo(1L);
        assertThat(typeOf(postId, userId)).isEqualTo("DISLIKE");
    }

    @Test
    void remove_deletes_row() {
        UUID postId = persistPost();
        UUID userId = UUID.randomUUID();
        reactionService.set(ReactionTarget.POST, postId, userId, ReactionType.LIKE);

        reactionService.remove(ReactionTarget.POST, postId, userId);

        assertThat(countRows("POST", postId, userId)).isZero();
    }

    @Test
    void remove_is_idempotent_when_absent() {
        UUID postId = persistPost();
        UUID userId = UUID.randomUUID();

        reactionService.remove(ReactionTarget.POST, postId, userId);

        assertThat(countRows("POST", postId, userId)).isZero();
    }

    @Test
    void set_on_deleted_post_is_blocked() {
        UUID postId = persistPost();
        Post post = postRepository.findById(postId).orElseThrow();
        post.softDelete();
        postRepository.save(post);
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                        () -> reactionService.set(
                                ReactionTarget.POST, postId, userId, ReactionType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REACTION_TARGET_NOT_FOUND);

        assertThat(countRows("POST", postId, userId)).isZero();
    }

    @Test
    void set_on_absent_post_is_blocked() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(
                        () -> reactionService.set(
                                ReactionTarget.POST, postId, userId, ReactionType.LIKE))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REACTION_TARGET_NOT_FOUND);
    }

    @Test
    void set_on_comment_target() {
        UUID postId = persistPost();
        Comment comment = commentRepository.save(
                Comment.create(postId, UUID.randomUUID(), null, "comment"));
        UUID commentId = comment.getId();
        UUID userId = UUID.randomUUID();

        reactionService.set(ReactionTarget.COMMENT, commentId, userId, ReactionType.LIKE);

        assertThat(countRows("COMMENT", commentId, userId)).isEqualTo(1L);
        assertThat(typeOf(commentId, userId)).isEqualTo("LIKE");
    }
}
