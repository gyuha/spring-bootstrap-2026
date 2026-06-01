package com.example.bootstrap.domain.reaction.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 리액션 upsert/DELETE/count MyBatis 매퍼 (BOARD-06/CMNT-05, D-73/D-74).
 *
 * <p>reaction은 JPA {@code @Entity}/Repository가 없는 MyBatis 전용 모델이다(C-1). 동시성 안전은 DB
 * {@code UNIQUE(target_type, target_id, user_id)} 복합 제약(04-01) + {@code INSERT ... ON CONFLICT
 * DO UPDATE}(upsert)로 보장하고, 제거는 하드 DELETE다. enum 파라미터는 {@code .name()} 문자열로
 * 바인딩한다(VARCHAR 컬럼, D-78). 실제 SQL은 동명의 XML 매퍼에 정의된다. {@code @MapperScan}이
 * {@code com.example.bootstrap.domain} 하위를 자동 스캔하므로 추가 배선은 불필요하다(D-68).
 */
@Mapper
public interface ReactionMapper {

    /**
     * (target_type, target_id, user_id)당 1행 멱등 upsert. 신규면 INSERT, 기존이면 type만 UPDATE.
     * id/created_at은 DB DEFAULT(gen_random_uuid()/NOW())에 맡긴다.
     */
    void upsert(
            @Param("targetType") String targetType,
            @Param("targetId") java.util.UUID targetId,
            @Param("userId") java.util.UUID userId,
            @Param("type") String type);

    /** 하드 DELETE — 행이 없어도 성공(멱등). */
    void delete(
            @Param("targetType") String targetType,
            @Param("targetId") java.util.UUID targetId,
            @Param("userId") java.util.UUID userId);

    /** 특정 target에 대한 type별 리액션 수(테스트 박제용 — 운영 카운트는 목록 매퍼가 담당). */
    long countByTarget(
            @Param("targetType") String targetType,
            @Param("targetId") java.util.UUID targetId,
            @Param("type") String type);
}
