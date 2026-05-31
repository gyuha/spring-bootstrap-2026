---
phase: 3
slug: bff-auth
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-31
---

# Phase 3 — Security

> Per-phase security contract: threat register, accepted risks, audit trail.
> 등록부는 3개 PLAN의 `<threat_model>` 블록에서 구성됨. mitigate 위협은 구현 + 28 GREEN(WireMock OIDC + Testcontainers)로 검증됨 → CLOSED.
> BFF 인증은 이 마일스톤의 보안 핵심(ASVS V2 Authentication · V3 Session Management).

---

## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| 브라우저 ↔ 백엔드(쿠키 세션) | 쿠키엔 SESSION ID만, 토큰 비노출. 신뢰 불가 클라이언트 |
| 백엔드 ↔ OIDC IdP(authorization-code/token) | 토큰 교환·검증은 서버에서만(BFF A-3). state/nonce/PKCE 경계 |
| auth ↔ identity 컨텍스트 | linkIdentity 호출 — 도메인과 닿는 유일 지점 |
| Redis 세션 직렬화 | OidcUser/OAuth2AuthorizedClient/BaselineOidcUser JDK 직렬화 왕복 |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-03-01 | Information Disclosure | findByEmail 포트 노출 | accept | 포트 내부 조회, 인증 브리지만 호출, 외부 노출 경로 없음 | closed |
| T-03-02 | Information Disclosure | 토큰 브라우저 노출(XSS) | mitigate | **HttpSessionOAuth2AuthorizedClientRepository 빈 명시 배선**(Spring 기본 InMemory 교정) → 토큰 세션→Redis 서버측만. 쿠키 HttpOnly. BffAuthIT.tokenOnlyInRedisNotInCookie 단언 | closed |
| T-03-03 | Tampering | CSRF(쿠키 세션) | mitigate | CookieCsrfTokenRepository.withHttpOnlyFalse + CsrfTokenRequestAttributeHandler + CsrfCookieFilter(쿠키 materialize). logout CSRF 요구. SC#4 GREEN | closed |
| T-03-04 | Spoofing | 세션 고정 | mitigate | Spring Security 기본 sessionManagement migrateSession | closed |
| T-03-05 | Elevation of Privilege | 미초대 사용자 무단 가입 | mitigate | IdentityLinkService가 findByEmail 부재 시 OAuth2AuthenticationException 거부(D-04). BffAuthIT 미초대 거부 단언 | closed |
| T-03-06 | Tampering/Spoofing | OAuth2 state/nonce 위조 | mitigate | oauth2Login 내장 state·nonce·PKCE(hand-roll 금지). MockOidcServer nonce echo + jwks 서명 검증 | closed |
| T-03-07 | Spoofing | principal 가변 식별자 사용 | mitigate | principal attribute user_id=불변 로컬 User.id(A-6). 연결 후 매칭 oid only | closed |
| T-03-08 | Information Disclosure | OIDC client-secret 커밋 노출 | mitigate | main application.yml에 OIDC registration 없음. 시크릿은 배포 프로파일/시크릿 매니저, 미커밋 | closed |
| T-03-09 | Information Disclosure | SC#1 토큰 비노출 검증 부실 | mitigate | WireMock 실 토큰 교환 + 세션 attribute(AUTHORIZED_CLIENTS) read-back 행위 단언(oidcLogin 우회 금지, ClientService 빈 미사용) | closed |
| T-03-10 | Spoofing | 로그아웃 후 세션 잔존 | mitigate | WireMock 세션 라운드트립 → logout 후 401 + sessionRepository.findById null(Redis 삭제) 단언 | closed |
| T-03-11 | Tampering | 직렬화 회귀(non-serializable) | mitigate | BaselineOidcUser Serializable + JDK 라운드트립 가드 테스트. JSON 전환 금지(Pitfall 1) | closed |
| T-03-SC | Tampering | Maven 의존성(oauth2-client/session-data-redis/security-test/wiremock) | mitigate | Spring 공식 4종 BOM 관리·Maven Central 실측. WireMock 3.13.1 좌표 실측 핀(test 스코프). SLOP 없음 | closed |

*Status: open · closed* / *Disposition: mitigate · accept · transfer*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-03-01 | T-03-01 | findByEmail은 포트 내부 조회. 인증 브리지만 호출, 외부 REST 노출 없음 — 저위험. | gyuha | 2026-05-31 |
| AR-03-02 | (운영) | 조건부 oauth2Login: 운영 OIDC registration 누락 시 로그인 불가(잠김, fail-CLOSED). 시작 WARN으로 fail-loud화. 실 IdP 연동 phase에서 재확인. | gyuha | 2026-05-31 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-31 | 12 | 12 | 0 | gsd-secure-phase (plan-time register, mitigations 28-GREEN 검증) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] mitigate 위협 완화책이 자동화 테스트(28 GREEN, WireMock OIDC)로 검증됨
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-31
