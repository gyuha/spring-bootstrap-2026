package com.anchors.baseline.auth.infrastructure.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * 로컬 User.id 를 운반하는 principal 캐리어(A-6 / Pattern 3). 위임 OidcUser 의 attribute 맵에
 * {@code user_id} 키로 로컬 User.id 를 병합해, 표준 {@code OidcUser.getAttribute("user_id")} 만으로
 * 로컬 User.id 를 읽게 한다(MeController 가 infrastructure 타입 없이 읽는 단일 경로).
 *
 * <p>Spring Session Redis 의 기본 JDK 직렬화 라운드트립을 위해 {@link Serializable} 을 구현한다(Pitfall 2).
 * delegate(DefaultOidcUser)·localUserId(Long)·병합 attribute 값이 모두 Serializable 이라 깨지지 않는다.
 */
public class BaselineOidcUser implements OidcUser, Serializable {

    public static final String USER_ID_ATTRIBUTE = "user_id";

    private final OidcUser delegate;
    private final Long localUserId;
    private final Map<String, Object> mergedAttributes;

    public BaselineOidcUser(OidcUser delegate, Long localUserId) {
        this.delegate = delegate;
        this.localUserId = localUserId;
        Map<String, Object> merged = new HashMap<>(delegate.getAttributes());
        merged.put(USER_ID_ATTRIBUTE, localUserId);
        this.mergedAttributes = Map.copyOf(merged);
    }

    /** 로컬 User.id 편의 접근자(infrastructure 내부·직렬화 가드 테스트용). */
    public Long getLocalUserId() {
        return localUserId;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return mergedAttributes;
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }
}
