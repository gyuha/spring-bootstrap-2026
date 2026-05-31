package com.anchors.baseline.auth.support;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * WireMock 으로 OIDC IdP 를 모킹하는 test 전용 fixture(D-08 (b)). 백엔드의 토큰 교환·id_token 검증
 * 경로(SC#1·SC#4·AUTH-01)를 실제로 태우기 위해 세 스텁을 제공한다 — discovery(.well-known),
 * token(/token: 서명된 id_token + 비어있지 않은 access/refresh 토큰), jwks(/jwks: id_token 검증용 공개키).
 *
 * <p>RSA 키쌍을 생성해 jwks 로 공개키를 노출하고 그 개인키로 id_token(JWT)을 서명한다. Spring Security 가
 * jwks_uri 로 공개키를 받아 id_token 서명을 검증한다. authorize 엔드포인트는 redirect_uri/state 를
 * 동적으로 echo 해야 해 WireMock 정적 스텁으로 표현하기 어렵다 — 콜백 구성은 테스트 코드가 직접 수행한다
 * (BffAuthIT 가 authorize 302 에서 state 를 추출해 콜백 URL 을 만든다).
 */
public class MockOidcServer implements AutoCloseable {

    private static final String AUTHORIZE_PATH = "/authorize";
    private static final String TOKEN_PATH = "/token";
    private static final String JWKS_PATH = "/jwks";
    private static final String USERINFO_PATH = "/userinfo";
    private static final String DISCOVERY_PATH = "/.well-known/openid-configuration";

    private final WireMockServer server;
    private final RSAKey rsaKey;
    private final RSAPrivateKey privateKey;

    public MockOidcServer() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA 키쌍 생성 실패", e);
        }
        this.server = new WireMockServer(WireMockConfiguration.options().dynamicPort());
    }

    /** WireMock 서버를 기동하고 discovery/jwks 스텁을 등록한다. issuer 가 baseUrl 에 의존하므로 start 후 등록. */
    public void start() {
        server.start();
        server.stubFor(get(urlPathEqualTo(DISCOVERY_PATH))
                .willReturn(okJson(discoveryDocument())));
        server.stubFor(get(urlPathEqualTo(JWKS_PATH))
                .willReturn(okJson(jwksJson())));
    }

    public void stop() {
        server.stop();
    }

    @Override
    public void close() {
        stop();
    }

    public String baseUrl() {
        return server.baseUrl();
    }

    public String authorizeUrl() {
        return baseUrl() + AUTHORIZE_PATH;
    }

    /**
     * /token 스텁을 등록한다. authorization-code 교환 시 주어진 subject(oid)/email/nonce 를 담아 서명한
     * id_token 과 비어있지 않은 access/refresh 토큰을 반환한다(SC#1 이 access token 존재를 단언).
     * nonce 는 authorize 리다이렉트에서 추출해 그대로 echo 해야 Spring 의 OidcIdTokenValidator 를 통과한다
     * (OIDC nonce 검증을 우회하지 않고 충실히 태운다).
     */
    public void stubToken(String subject, String email, String nonce) {
        String idToken = signIdToken(subject, email, nonce);
        String body = "{"
                + "\"access_token\":\"mock-access-token-" + UUID.randomUUID() + "\","
                + "\"refresh_token\":\"mock-refresh-token-" + UUID.randomUUID() + "\","
                + "\"token_type\":\"Bearer\","
                + "\"expires_in\":3600,"
                + "\"id_token\":\"" + idToken + "\""
                + "}";
        server.stubFor(post(urlPathEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));

        // OidcUserService(super.loadUser)가 email scope 때문에 userinfo 를 조회한다 — sub/email echo.
        // userinfo 의 sub 는 id_token 의 sub 와 일치해야 한다(OidcUserService 검증).
        server.stubFor(get(urlPathEqualTo(USERINFO_PATH))
                .willReturn(okJson("{\"sub\":\"" + subject + "\",\"email\":\"" + email
                        + "\",\"name\":\"Mock " + subject + "\"}")));
    }

    private String signIdToken(String subject, String email, String nonce) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder()
                    .issuer(baseUrl())
                    .subject(subject)
                    .audience("test-client-id")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(3600)))
                    .claim("email", email)
                    .claim("name", "Mock " + subject);
            if (nonce != null) {
                builder.claim("nonce", nonce);
            }
            JWTClaimsSet claims = builder.build();
            SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                    claims);
            jwt.sign(new RSASSASigner(privateKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("id_token 서명 실패", e);
        }
    }

    private String discoveryDocument() {
        String issuer = baseUrl();
        return toJson(Map.of(
                "issuer", issuer,
                "authorization_endpoint", issuer + AUTHORIZE_PATH,
                "token_endpoint", issuer + TOKEN_PATH,
                "jwks_uri", issuer + JWKS_PATH,
                "userinfo_endpoint", issuer + "/userinfo",
                "response_types_supported", new String[] {"code"},
                "subject_types_supported", new String[] {"public"},
                "id_token_signing_alg_values_supported", new String[] {"RS256"},
                "scopes_supported", new String[] {"openid", "email"}));
    }

    private String jwksJson() {
        return new JWKSet(rsaKey.toPublicJWK()).toString();
    }

    private static String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof String[] arr) {
                sb.append('[');
                for (int i = 0; i < arr.length; i++) {
                    if (i > 0) {
                        sb.append(',');
                    }
                    sb.append('"').append(arr[i]).append('"');
                }
                sb.append(']');
            } else {
                sb.append('"').append(v).append('"');
            }
        }
        return sb.append('}').toString();
    }
}
