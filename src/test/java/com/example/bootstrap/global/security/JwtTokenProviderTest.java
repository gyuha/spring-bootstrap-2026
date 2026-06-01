package com.example.bootstrap.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bootstrap.domain.user.entity.Role;
import com.example.bootstrap.global.BaseIntegrationTest;
import com.example.bootstrap.global.security.JwtTokenProvider.IssuedAccess;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.TestPropertySource;

/**
 * Access JWT 발급/디코딩 라운드트립 테스트 (AUTH-02).
 *
 * <p>실제 배선된 {@link NimbusJwtDecoder} 빈(블랙리스트 Validator 합성 포함)을 주입해
 * 발급기가 만든 토큰을 같은 키로 디코딩·검증한다. 빈 로직을 미러링하지 않고 실 빈을 검증한다.
 * 테스트용 secret/짧은 access-ttl을 프로퍼티로 주입한다.
 */
@TestPropertySource(properties = {
        "auth.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long-1234567890",
        "auth.jwt.access-ttl=5m"
})
class JwtTokenProviderTest extends BaseIntegrationTest {

    @Autowired
    JwtTokenProvider provider;

    @Autowired
    NimbusJwtDecoder decoder;

    @Test
    void issueAccess_roundTrips_throughRealDecoder() {
        UUID userId = UUID.randomUUID();
        Instant before = Instant.now();

        IssuedAccess issued = provider.issueAccess(userId, Role.ADMIN);

        Jwt decoded = decoder.decode(issued.token());
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getId()).isEqualTo(issued.jti());
        assertThat(decoded.<String>getClaim("role")).isEqualTo(Role.ADMIN.name());

        // exp는 발급 시점 + access-ttl(5m) 이내여야 한다
        assertThat(issued.exp()).isAfter(before);
        assertThat(issued.exp()).isBeforeOrEqualTo(Instant.now().plus(Duration.ofMinutes(5)));
        // JWT exp(NumericDate)는 초 단위 정밀도이므로 디코딩값은 초로 절삭된다
        assertThat(decoded.getExpiresAt()).isEqualTo(issued.exp().truncatedTo(ChronoUnit.SECONDS));
    }
}
