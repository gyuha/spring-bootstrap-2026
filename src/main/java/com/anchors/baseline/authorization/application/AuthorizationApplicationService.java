package com.anchors.baseline.authorization.application;

import com.anchors.baseline.authorization.domain.model.GlobalRoleGrant;
import com.anchors.baseline.authorization.domain.model.Group;
import com.anchors.baseline.authorization.domain.model.GroupMember;
import com.anchors.baseline.authorization.domain.model.MenuGrant;
import com.anchors.baseline.authorization.domain.model.ResourceGrant;
import com.anchors.baseline.authorization.domain.model.ResourceHierarchy;
import com.anchors.baseline.authorization.domain.model.RoleName;
import com.anchors.baseline.authorization.domain.repository.GlobalRoleGrantRepository;
import com.anchors.baseline.authorization.domain.repository.GroupMemberRepository;
import com.anchors.baseline.authorization.domain.repository.GroupRepository;
import com.anchors.baseline.authorization.domain.repository.MenuGrantRepository;
import com.anchors.baseline.authorization.domain.repository.ResourceGrantRepository;
import com.anchors.baseline.authorization.domain.repository.ResourceHierarchyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authorization 유스케이스 조율 + 트랜잭션 경계(grant/revoke). 비즈니스 규칙은 없다 —
 * XOR·식별자 가드 등 불변식은 모두 도메인 애그리거트(팩토리)에 있고, 여기서는 6개 쓰기 포트만 조율한다(D-04).
 * INVITED 사용자에게도 동일 경로로 부여가 영속된다 — userId 존재 검사 없음(soft ref D-07 / AUTHZ-09).
 * 인가 판정(evaluate/listObjects)은 이 서비스가 아니라 AuthorizationPort 책임이다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AuthorizationApplicationService {

    private final GlobalRoleGrantRepository globalRoleGrantRepository;
    private final MenuGrantRepository menuGrantRepository;
    private final ResourceGrantRepository resourceGrantRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ResourceHierarchyRepository resourceHierarchyRepository;

    // AUTHZ-01: 전역 역할
    public void grantGlobalRole(long userId, RoleName role) {
        globalRoleGrantRepository.save(GlobalRoleGrant.of(userId, role));
    }

    public void revokeGlobalRole(long userId, RoleName role) {
        globalRoleGrantRepository.deleteByUserIdAndRole(userId, role);
    }

    // AUTHZ-02: 메뉴 권한
    public void grantMenuToUser(long userId, long menuId, RoleName role) {
        menuGrantRepository.save(MenuGrant.forUser(userId, menuId, role));
    }

    public void grantMenuToGroup(long groupId, long menuId, RoleName role) {
        menuGrantRepository.save(MenuGrant.forGroup(groupId, menuId, role));
    }

    public void revokeMenuFromUser(long userId, long menuId) {
        menuGrantRepository.deleteByUserIdAndMenuId(userId, menuId);
    }

    public void revokeMenuFromGroup(long groupId, long menuId) {
        menuGrantRepository.deleteByGroupIdAndMenuId(groupId, menuId);
    }

    // AUTHZ-03: 리소스 권한
    public void grantResourceToUser(long userId, long resourceId, RoleName role) {
        resourceGrantRepository.save(ResourceGrant.forUser(userId, resourceId, role));
    }

    public void grantResourceToGroup(long groupId, long resourceId, RoleName role) {
        resourceGrantRepository.save(ResourceGrant.forGroup(groupId, resourceId, role));
    }

    public void revokeResourceFromUser(long userId, long resourceId) {
        resourceGrantRepository.deleteByUserIdAndResourceId(userId, resourceId);
    }

    public void revokeResourceFromGroup(long groupId, long resourceId) {
        resourceGrantRepository.deleteByGroupIdAndResourceId(groupId, resourceId);
    }

    // AUTHZ-04: 그룹 + 멤버십
    public Long createGroup(String name) {
        Group saved = groupRepository.save(Group.create(name));
        return saved.getId();
    }

    public void addMember(long groupId, long userId) {
        groupMemberRepository.save(GroupMember.of(groupId, userId));
    }

    public void removeMember(long groupId, long userId) {
        groupMemberRepository.deleteByGroupIdAndUserId(groupId, userId);
    }

    // AUTHZ-05: 리소스 계층
    public void defineHierarchy(long resourceId, Long parentResourceId) {
        resourceHierarchyRepository.save(ResourceHierarchy.of(resourceId, parentResourceId));
    }
}
