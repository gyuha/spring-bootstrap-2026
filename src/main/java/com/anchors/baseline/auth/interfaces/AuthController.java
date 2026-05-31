package com.anchors.baseline.auth.interfaces;

import com.anchors.baseline.authorization.application.AuthorizationApplicationService;
import com.anchors.baseline.authorization.application.PermissionView;
import com.anchors.baseline.identity.application.IdentityApplicationService;
import com.anchors.baseline.identity.application.UserView;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * BFF 인증 세션 엔드포인트(AUTH-06/10). SPA 가 401 노이즈 없이 인증 여부를 판별하고(/session)
 * OIDC 로그인에 진입(/login)하는 표면을 제공한다.
 *
 * <p>{@link MeController} 의 표준 {@code OidcUser.getAttribute("user_id")}(A-6) 읽기 패턴을 계승해
 * infrastructure 의 OIDC principal 구현 타입을 import/cast 하지 않는다 — ArchUnit
 * interfaces→infrastructure 게이트를 구조적으로 통과한다(D-06).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * OIDC registration 빈은 main 프로파일에 없을 수 있다(test/배포 프로파일에서만 정의). 빈 부재 시
     * 로그인 진입을 503 으로 안전 실패시키기 위해 {@link ObjectProvider} 로 지연 조회한다.
     */
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

    /**
     * 로그인 진입 시 사용할 OIDC registration id. 베이스라인은 단일 registration 을 전제하되, 복제 프로젝트가
     * 자신의 registration 명을 쓸 수 있도록 외부화한다(미설정 시 test/예제 기본값 {@code test-idp}).
     */
    private final String oidcRegistrationId;

    /** /api/auth/me 신원·권한 집계용 — 교차 컨텍스트 application 서비스(ArchUnit interfaces→application 허용, D-06). */
    private final IdentityApplicationService identityService;
    private final AuthorizationApplicationService authorizationService;

    public AuthController(ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
                          @Value("${app.auth.oidc-registration-id:test-idp}") String oidcRegistrationId,
                          IdentityApplicationService identityService,
                          AuthorizationApplicationService authorizationService) {
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.oidcRegistrationId = oidcRegistrationId;
        this.identityService = identityService;
        this.authorizationService = authorizationService;
    }

    /**
     * 인증 상태 조회(AUTH-06). permitAll 매처에 등록되어(SecurityConfig) 비인증 접근 시 401 이 아니라
     * 200 + {@code {authenticated:false}} 를 반환한다 — SPA 가 401 노이즈 없이 로그인 여부를 판별한다(D-03).
     */
    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal OidcUser principal) {
        if (principal == null) {
            return new SessionResponse(false, null);
        }
        return new SessionResponse(true, principal.getAttribute("user_id"));
    }

    /**
     * 신원·권한 집계 엔드포인트(AUTH-08/09). 인증 사용자의 신원(userId/email/status)과 직접 부여 권한
     * (roles/menus/resources)을 단일 응답으로 반환한다. 비인증 시 SecurityConfig {@code /api/**} 보호 매처가
     * 401 을 먼저 응답하므로 principal null 분기가 없다(D-05). 권한은 직접 부여(direct grant)만 포함 —
     * 그룹·계층 상속 미포함(D-02).
     */
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal OidcUser principal) {
        // 인증은 통과했으나 user_id attribute 가 없거나(미배선·표준 OidcUser) 숫자가 아니면 신원 식별 불가 →
        // 500 이 아니라 401 로 안전 실패한다(MeController 의 null-tolerant 선례와 일관). instanceof Number 로
        // null·타입불일치를 동시에 막는다. IdP claim 타입에 따라 Integer/Long 모두 longValue() 로 정규화.
        if (!(principal.getAttribute("user_id") instanceof Number rawId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "principal 에 user_id attribute 가 없습니다");
        }
        long userId = rawId.longValue();
        UserView identity = identityService.findUser(userId);
        PermissionView permissions = authorizationService.findPermissions(userId);
        return MeResponse.of(identity, permissions);
    }

    /**
     * OIDC 로그인 진입 BFF 래퍼(AUTH-10). 프레임워크 {@code /oauth2/authorization/{registration}} 진입점을
     * 감싸고, 로그인 후 복귀 경로(returnTo)를 세션에 저장한다(SecurityConfig successHandler 가 복원).
     *
     * <p>returnTo 는 신뢰 불가 입력이므로 {@link #isSafeRelativePath}(상대경로) 검증을 통과할 때만 저장한다
     * (open-redirect 방지 — Spring Security 는 이를 자동 보호하지 않음, HIGH).
     */
    @GetMapping("/login")
    public void login(@RequestParam(required = false) String returnTo,
                      HttpSession session,
                      HttpServletResponse response) throws IOException {
        // registration 부재(503) 가드를 RETURN_TO 저장보다 먼저 — 503 으로 끝날 때 stale attribute 가
        // 세션에 남아 다른 탭의 정상 로그인 successHandler 를 오염시키는 것을 막는다.
        if (clientRegistrationRepository.getIfAvailable() == null) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "OIDC registration not configured");
            return;
        }
        if (isSafeRelativePath(returnTo)) {
            session.setAttribute("RETURN_TO", returnTo);
        }
        response.sendRedirect("/oauth2/authorization/" + oidcRegistrationId);
    }

    /**
     * open-redirect 방지: 절대 URL·프로토콜-상대 URL(//host)·스킴 포함 URL·역슬래시 포함 URL 을 모두 거부하고
     * 상대경로만 허용한다. 역슬래시 거부는 {@code /\evil.com} 같은 입력을 브라우저가 {@code //evil.com} 으로
     * 정규화(WHATWG URL)해 외부 리다이렉트되는 우회를 차단한다.
     */
    private static boolean isSafeRelativePath(String url) {
        return url != null
                && url.startsWith("/")
                && !url.startsWith("//")
                && !url.contains("\\")
                && !url.contains("://");
    }

    /** 세션 상태 응답. userId 는 비인증 시 null 이며 {@code NON_NULL} 로 직렬화에서 생략된다(Map.of NPE 회피). */
    public record SessionResponse(
            boolean authenticated,
            @JsonInclude(JsonInclude.Include.NON_NULL) Object userId) {
    }
}
