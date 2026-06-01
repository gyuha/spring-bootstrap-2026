package com.example.bootstrap.domain.comment.repository;

import com.example.bootstrap.domain.comment.entity.Comment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Comment JPA 저장소 (쓰기/단건 CRUD, D-08).
 *
 * <p>BaseEntity의 {@code @SQLRestriction("deleted_at is null")}이 모든 조회에 자동 적용되므로,
 * soft-delete된 행은 findById/findAll에서 자동 제외된다(D-50 계승).
 */
public interface CommentRepository extends JpaRepository<Comment, UUID> {
}
