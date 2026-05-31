package com.anchors.baseline.authorization.domain.repository;

import com.anchors.baseline.authorization.domain.model.Group;
import java.util.Optional;

/**
 * Group 쓰기 포트 — 도메인이 정의하고 infrastructure(JPA)가 구현한다(헥사고날 포트).
 * 도메인 순수 — infrastructure import 금지.
 */
public interface GroupRepository {

    Group save(Group group);

    Optional<Group> findById(long id);

    Optional<Group> findByName(String name);
}
