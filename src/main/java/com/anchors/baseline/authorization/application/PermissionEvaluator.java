package com.anchors.baseline.authorization.application;

import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import com.anchors.baseline.authorization.domain.model.RoleName;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 통합 판정 — evaluate(userId, resource, action) = (직접 부여) ∪ (소속 그룹) ∪ (상위 리소스 상속)에
 * 역할 함의(EDITOR ⊇ VIEWER) 규칙을 적용해 boolean 을 낸다(정본 §5.4 / D-05).
 *
 * <p>배치 결정(Open Q1 / D-03): 정본은 "도메인 서비스"라 부르나, 읽기 포트(PermissionReadPort)는
 * application 에 있고 domain 은 어떤 레이어도 의존할 수 없으므로(ArchUnit mayNotAccessAnyLayer)
 * 이 클래스를 application 에 둔다(application→domain 만 허용). Phase 1 SampleQuery 가 application 인 것과 동형.
 *
 * <p>D-03: Spring Security 의 동명 인터페이스(org.springframework.security.access.PermissionEvaluator,
 * 메서드 hasPermission)를 import/구현하지 않는다 — 도메인/판정 로직을 framework 에 오염시키지 않는다(A-4).
 * Spring 통합이 필요하면 별도 어댑터를 infrastructure 에 둔다(Wave 2, 선택).
 *
 * <p>D-06: 캐시 없이 매 호출 시 읽기 포트로 DB 조회 → 부여/회수 즉시 반영(SC#1).
 */
@Service
public class PermissionEvaluator {

    private final PermissionReadPort readPort;

    public PermissionEvaluator(PermissionReadPort readPort) {
        this.readPort = readPort;
    }

    public boolean evaluate(long userId, ResourceId resource, Action action) {
        List<EffectiveGrantDto> grants = readPort.findEffectiveGrants(userId, resource.value());
        // default-deny: 부여가 없으면 false (ASVS V4)
        return grants.stream()
            .anyMatch(grant -> RoleName.valueOf(grant.getRole()).implies(action));
    }
}
