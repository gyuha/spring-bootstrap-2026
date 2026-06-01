package com.example.bootstrap.domain.post.entity;

import com.example.bootstrap.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시글 엔티티 (BOARD-02).
 *
 * <p>{@link BaseEntity}를 상속해 UUIDv7 PK, audit 시각(UTC), soft-delete, 낙관적 락을 자동으로 얻는다.
 * 연관관계는 {@code @ManyToOne} 대신 FK UUID 컬럼으로 직접 매핑한다(D-70 — 목록은 MyBatis 평탄
 * DTO이므로 JPA 연관 그래프 lazy 로딩에 의존하지 않는다).
 */
@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Post extends BaseEntity {

    @Column(name = "board_id", nullable = false, columnDefinition = "uuid")
    private UUID boardId;

    @Column(name = "author_id", nullable = false, columnDefinition = "uuid")
    private UUID authorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    private Post(UUID boardId, UUID authorId, String title, String content) {
        this.boardId = boardId;
        this.authorId = authorId;
        this.title = title;
        this.content = content;
    }

    public static Post create(UUID boardId, UUID authorId, String title, String content) {
        return new Post(boardId, authorId, title, content);
    }

    /**
     * 제목/내용을 교체한다(BOARD-04). 캡슐화를 위해 setter 대신 도메인 메서드로 노출한다. JPA dirty
     * checking이 UPDATE를 발행하고, {@code @Version} 낙관적 락(BaseEntity)이 동시 변경을 보호한다.
     */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }
}
