package com.anchors.baseline.auth.interfaces;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    public AuthController(ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
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
        if (isSafeRelativePath(returnTo)) {
            session.setAttribute("RETURN_TO", returnTo);
        }
        if (clientRegistrationRepository.getIfAvailable() == null) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "OIDC registration not configured");
            return;
        }
        response.sendRedirect("/oauth2/authorization/test-idp");
    }

    /** open-redirect 방지: 절대 URL·프로토콜-상대 URL(//host)·스킴 포함 URL 을 모두 거부하고 상대경로만 허용한다. */
    private static boolean isSafeRelativePath(String url) {
        return url != null
                && url.startsWith("/")
                && !url.startsWith("//")
                && !url.contains("://");
    }

    /** 세션 상태 응답. userId 는 비인증 시 null 이며 {@code NON_NULL} 로 직렬화에서 생략된다(Map.of NPE 회피). */
    public record SessionResponse(
            boolean authenticated,
            @JsonInclude(JsonInclude.Include.NON_NULL) Object userId) {
    }
}
