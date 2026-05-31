package com.anchors.baseline.identity.infrastructure.jpa;

import com.anchors.baseline.identity.domain.model.User;
import com.anchors.baseline.identity.domain.repository.UserRepository;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * UserRepository 포트의 Spring Data JPA 어댑터.
 * JpaRepository 가 save(User)/findById 를 기본 제공하고,
 * existsByEmail(Email)·findByExternalId(String) 은 파생 쿼리로 자동 구현된다.
 * existsByEmail 의 Email 파라미터는 @Embeddable(필드 value → 컬럼 email)로 매핑된다.
 */
public interface UserJpaRepository
        extends JpaRepository<User, Long>, UserRepository {
}
