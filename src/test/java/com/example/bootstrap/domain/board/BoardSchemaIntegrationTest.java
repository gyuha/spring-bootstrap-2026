package com.example.bootstrap.domain.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bootstrap.domain.board.entity.Board;
import com.example.bootstrap.domain.board.repository.BoardRepository;
import com.example.bootstrap.domain.comment.entity.Comment;
import com.example.bootstrap.domain.comment.repository.CommentRepository;
import com.example.bootstrap.domain.post.entity.Post;
import com.example.bootstrap.domain.post.repository.PostRepository;
import com.example.bootstrap.global.BaseIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase 4 wave-1 스키마/엔티티 정합 통합 테스트 (D-78/D-79).
 *
 * <p>앱 컨텍스트 부팅 자체가 V3 적용 + {@code ddl-auto=validate} 정합을 검증한다 — 매핑된
 * board/post/comment 3개 엔티티와 V3 스키마가 일치하지 않으면 컨텍스트 로딩이 실패한다.
 * reactions는 JPA {@code @Entity}가 없어 validate 대상에서 제외되므로, UNIQUE 복합 제약은
 * {@link JdbcTemplate} native INSERT로 직접 박제한다(P6/D-74).
 */
class BoardSchemaIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private BoardRepository boardRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Board 저장 시 UUIDv7 PK·UTC audit·version이 자동 기록된다. */
    @Test
    void boardPersist() {
        Board saved = boardRepository.save(Board.create("자유게시판"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getName()).isEqualTo("자유게시판");
    }

    /** Post 저장 시 board_id/author_id FK 컬럼이 매핑되고 audit가 UTC로 기록된다. */
    @Test
    void postPersist() {
        Board board = boardRepository.save(Board.create("게시판"));
        UUID authorId = UUID.randomUUID();

        Post saved = postRepository.save(Post.create(board.getId(), authorId, "제목", "내용"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getBoardId()).isEqualTo(board.getId());
        assertThat(saved.getAuthorId()).isEqualTo(authorId);
    }

    /** 부모 댓글 저장 후 그 id를 parentCommentId로 갖는 1단계 대댓글 저장이 성공한다(CMNT-02). */
    @Test
    void commentReplyPersist() {
        Board board = boardRepository.save(Board.create("게시판"));
        UUID authorId = UUID.randomUUID();
        Post post = postRepository.save(Post.create(board.getId(), authorId, "제목", "내용"));

        Comment parent =
                commentRepository.save(Comment.create(post.getId(), authorId, null, "부모 댓글"));
        Comment reply =
                commentRepository.save(
                        Comment.create(post.getId(), authorId, parent.getId(), "대댓글"));

        assertThat(parent.getId()).isNotNull();
        assertThat(parent.getParentCommentId()).isNull();
        assertThat(reply.getId()).isNotNull();
        assertThat(reply.getParentCommentId()).isEqualTo(parent.getId());
    }

    /**
     * 같은 (target_type, target_id, user_id)로 reactions에 2건 INSERT 시 두 번째가 UNIQUE 복합
     * 제약 위반으로 실패한다(P6/D-74 — 동시 dup-vote 차단의 DB 레벨 근간). reactions는 JPA
     * 엔티티가 없으므로 {@link JdbcTemplate} native INSERT로 제약을 직접 박제한다.
     */
    @Test
    void reactionUniqueConstraint() {
        UUID targetId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String insert =
                "INSERT INTO reactions (target_type, target_id, user_id, type) "
                        + "VALUES (?, ?, ?, ?)";

        jdbcTemplate.update(insert, "POST", targetId, userId, "LIKE");

        assertThatThrownBy(() -> jdbcTemplate.update(insert, "POST", targetId, userId, "DISLIKE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
