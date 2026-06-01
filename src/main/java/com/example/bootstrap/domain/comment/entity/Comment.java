package com.example.bootstrap.domain.comment.entity;

import com.example.bootstrap.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 댓글 엔티티 (CMNT-01/CMNT-02).
 *
 * <p>{@link BaseEntity}를 상속해 UUIDv7 PK, audit 시각(UTC), soft-delete, 낙관적 락을 자동으로 얻는다.
 * {@code parentCommentId}가 null이면 루트 댓글, 값이 있으면 1단계 대댓글이다(2단계 이상 중첩 불가 —
 * CMNT-02). 연관관계는 FK UUID 컬럼 직접 매핑(D-70).
 */
@Entity
@Table(name = "comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Comment extends BaseEntity {

    @Column(name = "post_id", nullable = false, columnDefinition = "uuid")
    private UUID postId;

    @Column(name = "parent_comment_id", columnDefinition = "uuid")
    private UUID parentCommentId;

    @Column(name = "author_id", nullable = false, columnDefinition = "uuid")
    private UUID authorId;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private Comment(UUID postId, UUID authorId, UUID parentCommentId, String content) {
        this.postId = postId;
        this.authorId = authorId;
        this.parentCommentId = parentCommentId;
        this.content = content;
    }

    /**
     * 댓글/대댓글을 생성한다. {@code parentCommentId}가 null이면 루트 댓글, 값이 있으면 1단계
     * 대댓글이다(CMNT-02).
     */
    public static Comment create(
            UUID postId, UUID authorId, UUID parentCommentId, String content) {
        return new Comment(postId, authorId, parentCommentId, content);
    }
}
