package com.example.bootstrap.global.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HS256 JWT 발급/검증 인프라 빈 (AUTH-02/AUTH-05, D-20/D-22/D-28/D-29/D-32).
 *
 * <p>SS 7.0은 인코더/디코더 모두 {@code withSecretKey(SecretKey)} 빌더를 제공해 대칭으로 배선된다.
 * 메서드명 비대칭 주의 — 인코더는 {@code .algorithm(...)}, 디코더는 {@code .macAlgorithm(...)}.
 * 디코더에는 기본 검증(exp/nbf)에 더해 jti 블랙리스트 Validator를 합성 주입해 decode 단계에서 401을 낸다.
 */
@Configuration
public class JwtBeans {

    /** HS256 대칭키. secret이 32byte 미만이면 빈 생성 시 즉시 실패한다 (D-22). */
    @Bean
    SecretKey jwtSecretKey(AuthProperties props) {
        byte[] bytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("auth.jwt.secret must be >= 32 bytes for HS256");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    NimbusJwtEncoder jwtEncoder(SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /** 기본 검증(exp/nbf) + jti 블랙리스트 검사를 합성한 디코더 (D-29). */
    @Bean
    NimbusJwtDecoder jwtDecoder(SecretKey key, BlacklistTokenValidator blacklist) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefault(),
                blacklist));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
