package com.anchors.baseline.identity.domain.repository;

import com.anchors.baseline.identity.domain.model.Email;
import com.anchors.baseline.identity.domain.model.User;
import java.util.Optional;

/**
 * User 영속화 포트 — 도메인이 정의하고 infrastructure 가 구현한다(헥사고날 포트).
 * 도메인 순수 — infrastructure import 금지.
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    boolean existsByEmail(Email email);

    Optional<User> findByEmail(Email email);

    Optional<User> findByExternalId(String externalId);
}
