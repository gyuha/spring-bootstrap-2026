package com.example.bootstrap.domain.comment.mapper;

import com.example.bootstrap.domain.comment.dto.CommentListItem;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 댓글 복잡 조회용 MyBatis 매퍼 — 목록 페이징 + 리액션 카운트 집계 (CMNT-03, QUERY-01, D-72).
 *
 * <p>JPA {@code @SQLRestriction}이 MyBatis에는 적용되지 않으므로 목록·카운트 SELECT 모두
 * {@code comments.deleted_at IS NULL}을 수동 강제한다(D-72b/P7). 또한 삭제된 부모 post의 댓글을
 * SQL 레벨에서 제외하기 위해 {@code JOIN posts ... AND posts.deleted_at IS NULL}을 강제한다
 * (M-1 — 컨트롤러 재량에 위임 금지). 리액션 카운트는 목록 1건당 반복 쿼리(N+1)나 행별 상관
 * 서브쿼리가 아니라, 단일 {@code LEFT JOIN reactions ... GROUP BY comments.id} +
 * {@code COUNT(*) FILTER (...)}로 일괄 집계한다 — 목록 크기와 무관하게 실행 SQL이 상수다(D-72/P9).
 * {@code @MapperScan(domain)}이 자동 스캔하므로 추가 배선은 불필요하다(D-68).
 */
@Mapper
public interface CommentQueryMapper {

    List<CommentListItem> findPage(
            @Param("postId") UUID postId,
            @Param("offset") long offset,
            @Param("limit") int limit);

    long countByPost(@Param("postId") UUID postId);
}
