package com.example.bootstrap.domain.user.repository;

import com.example.bootstrap.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * User JPA 저장소 (쓰기/단건 조회, D-08).
 *
 * <p>BaseEntity의 {@code @SQLRestriction("deleted_at is null")}이 모든 조회에 자동 적용되므로,
 * soft-delete된 사용자는 {@link #findByEmail}/{@link #existsByEmail}에서 자동 제외된다(D-35).
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
