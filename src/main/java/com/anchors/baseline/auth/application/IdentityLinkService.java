package com.anchors.baseline.auth.application;

import com.anchors.baseline.identity.application.IdentityApplicationService;
import com.anchors.baseline.identity.domain.model.Email;
import com.anchors.baseline.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * auth.application 브리지(D-01) — OIDC 성공 시점에 OIDC subject(oid)/email 을 로컬 User.id 로 해소하고
 * 최초 로그인이면 identity 컨텍스트의 linkIdentity 를 조율한다. 도메인과 닿는 유일 지점(정본 §5.1).
 *
 * <p>비즈니스 규칙 없음 — 전이·멱등성 가드는 모두 User 애그리거트 내부. 여기서는 포트(UserRepository)와
 * 같은 Application 레이어의 IdentityApplicationService 만 조율한다(ArchUnit ownLayer 허용 — 실증됨).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IdentityLinkService {

    private final UserRepository userRepository;
    private final IdentityApplicationService identityApplicationService;

    /**
     * OIDC subject(oid)/email → 로컬 User.id 해소(D-02 2단계 매칭).
     * (1) findByExternalId(oid) present → 재로그인(멱등, linkIdentity 미호출).
     * (2) else findByEmail(email) present → linkIdentity(INVITED→ACTIVE) 후 그 id 반환.
     * (3) 둘 다 부재 → 미초대 사용자 인증 거부(D-04 invite-first).
     */
    public Long resolveAndLink(String oid, String email, String displayName) {
        var existing = userRepository.findByExternalId(oid);
        if (existing.isPresent()) {
            return existing.get().getId();
        }
        var invited = userRepository.findByEmail(new Email(email))
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("unauthorized_user"), "초대되지 않은 사용자입니다: " + email));
        identityApplicationService.linkIdentity(invited.getId(), oid, displayName);
        return invited.getId();
    }
}
