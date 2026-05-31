package com.anchors.baseline.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.anchors.baseline.auth.infrastructure.security.BaselineOidcUser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/**
 * Pitfall 2 회귀 가드 — Spring 컨텍스트 없이 도는 빠른 단위 신호. {@link BaselineOidcUser} 가
 * JDK 직렬화(Redis 세션 저장 경로) 라운드트립을 통과하고, 역직렬화 후에도 localUserId 와
 * {@code getAttribute("user_id")} 가 보존됨을 단언한다. 불투명한 WireMock 통합 테스트보다 먼저
 * RED/GREEN 신호를 주어 BaselineOidcUser 의 비직렬화 필드 회귀를 정밀하게 잡는다.
 */
class BaselineOidcUserSerializationTest {

    @Test
    void roundTripPreservesLocalUserIdAndUserIdAttribute() throws Exception {
        Long localUserId = 42L;

        OidcIdToken idToken = new OidcIdToken(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of(
                        IdTokenClaimNames.SUB, "oid-123",
                        StandardClaimNames.EMAIL, "invited@example.com"));
        OidcUser delegate = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken,
                IdTokenClaimNames.SUB);

        BaselineOidcUser original = new BaselineOidcUser(delegate, localUserId);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }

        BaselineOidcUser restored;
        try (ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (BaselineOidcUser) in.readObject();
        }

        assertThat(restored.getLocalUserId()).isEqualTo(localUserId);
        assertThat(restored.<Object>getAttribute(BaselineOidcUser.USER_ID_ATTRIBUTE))
                .isEqualTo(localUserId);
    }
}
