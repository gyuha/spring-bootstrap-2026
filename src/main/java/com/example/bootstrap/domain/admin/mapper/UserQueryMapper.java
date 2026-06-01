package com.example.bootstrap.domain.admin.mapper;

import com.example.bootstrap.domain.admin.dto.AdminUserResponse;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 사용자 복잡 조회용 MyBatis 매퍼 — 관리자 목록 페이징 (ADMIN-01, D-46).
 *
 * <p>JPA의 {@code @SQLRestriction}이 MyBatis에는 적용되지 않으므로, 목록·카운트 SQL 모두
 * {@code deleted_at IS NULL}을 수동으로 강제한다(D-47, P7) — 삭제 사용자 노출 및 삭제 포함
 * 카운트를 차단한다. 실제 SQL은 동명의 XML 매퍼에 정의된다. {@code @MapperScan}이
 * {@code com.example.bootstrap.domain} 하위를 자동 스캔하므로 추가 배선은 불필요하다.
 */
@Mapper
public interface UserQueryMapper {

    List<AdminUserResponse> findPage(@Param("offset") long offset, @Param("limit") int limit);

    long countActive();
}
