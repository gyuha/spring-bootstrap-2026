package com.anchors.baseline.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.authorization.application.AuthorizationApplicationService;
import com.anchors.baseline.authorization.application.PermissionEvaluator;
import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import com.anchors.baseline.authorization.domain.model.RoleName;
import com.anchors.baseline.authorization.domain.repository.ResourceGrantRepository;
import com.anchors.baseline.identity.application.IdentityApplicationService;
import com.anchors.baseline.identity.domain.model.UserStatus;
import com.anchors.baseline.identity.domain.repository.UserRepository;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SC#4 (AUTHZ-09): INVITED 사용자에게 부여한 권한이 DB 에 저장되고, 로그인(linkIdentity → ACTIVE) 후
 * 동일 userId 로 효력이 있음을 증명한다. userId 는 soft reference 라 INVITED→ACTIVE 전이가
 * authorization 데이터에 무영향이므로 추가 코드 없이 충족된다(§4.1 / D-07).
 *
 * <p>VALIDATION 명령: linkIdentity 는 placeholder/mock 이 아니라 실 {@link IdentityApplicationService#linkIdentity}
 * 를 호출해 ACTIVE 전이를 실제로 일으킨다.
 */
class InvitedGrantSurfacingIT extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong(4_000_000L);

    private static long nextResourceId() {
        return SEQ.getAndIncrement();
    }

    @Autowired
    IdentityApplicationService identityService;

    @Autowired
    AuthorizationApplicationService authzService;

    @Autowired
    PermissionEvaluator permissionEvaluator;

    @Autowired
    ResourceGrantRepository resourceGrantRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void grantToInvitedUserSurvivesLoginAndIsEffective() {
        // 1) invite → INVITED userId
        Long userId = identityService.invite("invited-" + System.nanoTime() + "@x.com");
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
            .isEqualTo(UserStatus.INVITED);

        long resourceId = nextResourceId();

        // 2) INVITED userId 에 권한 부여 → DB 영속 단언(soft ref, 존재 검사 없음)
        authzService.grantResourceToUser(userId, resourceId, RoleName.EDITOR);
        assertThat(resourceGrantRepository.findByUserId(userId))
            .extracting(g -> g.getResourceId())
            .contains(resourceId);

        // 3) 실 linkIdentity 호출 → ACTIVE 전이 (placeholder/mock 아님 — VALIDATION 명령)
        identityService.linkIdentity(userId, "oid-" + System.nanoTime(), "초대된 사용자");
        assertThat(userRepository.findById(userId).orElseThrow().getStatus())
            .isEqualTo(UserStatus.ACTIVE);

        // 4) 동일 userId evaluate → true (전이가 권한에 무영향)
        assertThat(permissionEvaluator.evaluate(userId, ResourceId.of(resourceId), Action.EDIT))
            .isTrue();
    }
}
