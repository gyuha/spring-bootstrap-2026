package com.example.bootstrap.domain.admin.dto;

import com.example.bootstrap.domain.user.entity.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * 관리자용 사용자 응답 DTO (ADMIN-01/02, D-49).
 *
 * <p>password 해시는 절대 포함하지 않는다(D-49). MyBatis 조회 결과(컬럼 alias 기반 생성자
 * auto-mapping)와 JPA 단건 매핑에 공용 사용한다. {@code role}은 VARCHAR(enum STRING)에서
 * {@link Role}로 이름 기준 auto-map된다.
 *
 * @param createdAt 생성 시각 (UTC Instant, D-13)
 */
public record AdminUserResponse(UUID id, String email, Role role, Instant createdAt) {
}
