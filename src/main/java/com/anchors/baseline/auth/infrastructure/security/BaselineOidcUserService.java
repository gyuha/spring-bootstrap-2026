package com.anchors.baseline.auth.infrastructure.security;

import com.anchors.baseline.auth.application.IdentityLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * OIDC 성공 시점(UserInfo 처리)에 auth.application 브리지를 호출해 로컬 User.id 를 해소하고
 * principal 을 {@link BaselineOidcUser} 로 교체한다(AUTH-05 / Pattern 5). 프레임워크 타입 결합 →
 * infrastructure 배치(D-01). 표준 OIDC 처리·토큰 교환은 {@code super.loadUser} 에 위임.
 */
@Component
@RequiredArgsConstructor
public class BaselineOidcUserService extends OidcUserService {

    private final IdentityLinkService linkService;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String oid = oidcUser.getSubject();          // 불변 식별자(A-6)
        String email = oidcUser.getEmail();          // 표준 email claim(D-03 [가정])
        String displayName = oidcUser.getFullName();
        Long userId = linkService.resolveAndLink(oid, email, displayName);
        return new BaselineOidcUser(oidcUser, userId);
    }
}
