package com.example.bootstrap.account.controller;

import com.example.bootstrap.account.application.dto.AccountResponse;
import com.example.bootstrap.account.application.dto.LoginRequest;
import com.example.bootstrap.account.application.dto.LogoutRequest;
import com.example.bootstrap.account.application.dto.RefreshRequest;
import com.example.bootstrap.account.application.dto.RegisterRequest;
import com.example.bootstrap.account.application.dto.SocialAuthRequest;
import com.example.bootstrap.account.application.dto.TokenResponse;
import com.example.bootstrap.account.application.service.AccountService;
import com.example.bootstrap.account.application.service.AuthService;
import com.example.bootstrap.account.infrastructure.oauth2.AbstractOAuth2Handler;
import com.example.bootstrap.account.infrastructure.oauth2.GoogleOAuth2Handler;
import com.example.bootstrap.account.infrastructure.oauth2.KakaoOAuth2Handler;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 인증 API 컨트롤러.
 *
 * <p>회원가입, 이메일/비밀번호 로그인, 토큰 갱신, 로그아웃, 소셜 로그인 엔드포인트를 제공합니다.
 * 인증이 불필요한 공개 API이며 {@code /api/v1/auth/**} 경로로 노출됩니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accountService;
    private final AuthService authService;
    private final GoogleOAuth2Handler googleOAuth2Handler;
    private final KakaoOAuth2Handler kakaoOAuth2Handler;

    /**
     * @param accountService       계정 CRUD 서비스
     * @param authService          인증 토큰 서비스
     * @param googleOAuth2Handler  Google 소셜 로그인 핸들러
     * @param kakaoOAuth2Handler   Kakao 소셜 로그인 핸들러
     */
    public AuthController(
            final AccountService accountService,
            final AuthService authService,
            final GoogleOAuth2Handler googleOAuth2Handler,
            final KakaoOAuth2Handler kakaoOAuth2Handler) {
        this.accountService = accountService;
        this.authService = authService;
        this.googleOAuth2Handler = googleOAuth2Handler;
        this.kakaoOAuth2Handler = kakaoOAuth2Handler;
    }

    /**
     * 신규 계정을 등록합니다.
     *
     * @param request 회원가입 요청
     * @return 201 Created + 생성된 계정 정보
     * @throws BusinessException 중복 이메일인 경우 {@link ErrorCode#ACCOUNT_001}
     */
    @PostMapping("/register")
    public Mono<ResponseEntity<ApiResponse<AccountResponse>>> register(
            @Valid @RequestBody final RegisterRequest request) {
        return accountService.register(request)
                .map(response -> ResponseEntity
                        .status(HttpStatus.CREATED)
                        .body(ApiResponse.success("회원가입이 완료되었습니다.", response)));
    }

    /**
     * 이메일/비밀번호 로그인을 처리합니다.
     *
     * @param request 로그인 요청
     * @return 200 OK + Access/Refresh 토큰
     * @throws BusinessException 이메일/비밀번호 불일치 {@link ErrorCode#AUTH_004}
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<TokenResponse>>> login(
            @Valid @RequestBody final LoginRequest request) {
        return authService.login(request)
                .map(response -> ResponseEntity.ok(
                        ApiResponse.success("로그인이 완료되었습니다.", response)));
    }

    /**
     * Refresh Token을 사용해 새 토큰을 발급합니다 (Rotation).
     *
     * @param request Refresh Token 요청
     * @return 200 OK + 새 Access/Refresh 토큰
     * @throws BusinessException 만료/블랙리스트/재사용 토큰인 경우 AUTH_002~005
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<ApiResponse<TokenResponse>>> refresh(
            @Valid @RequestBody final RefreshRequest request) {
        return authService.refresh(request.refreshToken())
                .map(response -> ResponseEntity.ok(
                        ApiResponse.success("토큰이 갱신되었습니다.", response)));
    }

    /**
     * 로그아웃을 처리합니다.
     *
     * <p>Authorization 헤더에서 Access Token을 직접 읽습니다.
     * JwtAuthenticationFilter를 통과한 후 userId를 알 수 있지만,
     * 로그아웃은 토큰을 직접 블랙리스트에 등록해야 하므로 원본 토큰이 필요합니다.
     *
     * @param authorization Authorization 헤더 (Bearer 포함)
     * @param request       Refresh Token 포함 로그아웃 요청
     * @return 200 OK
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<Void>>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION) final String authorization,
            @Valid @RequestBody final LogoutRequest request) {
        String accessToken = authorization.substring(7);
        return authService.logout(accessToken, request.refreshToken())
                .thenReturn(ResponseEntity.<ApiResponse<Void>>ok(
                        ApiResponse.success("로그아웃이 완료되었습니다.")));
    }

    /**
     * 소셜 로그인을 처리합니다.
     *
     * @param provider provider 이름 (google / kakao)
     * @param request  provider access token
     * @return 200 OK + Access/Refresh 토큰
     * @throws BusinessException 미지원 provider {@link ErrorCode#AUTH_006}
     */
    @PostMapping("/social/{provider}")
    public Mono<ResponseEntity<ApiResponse<TokenResponse>>> socialLogin(
            @PathVariable final String provider,
            @Valid @RequestBody final SocialAuthRequest request) {
        AbstractOAuth2Handler handler = resolveHandler(provider);
        return handler.authenticate(request.accessToken())
                .map(authResponse -> ResponseEntity.ok(
                        ApiResponse.success("소셜 로그인이 완료되었습니다.",
                                new TokenResponse(authResponse.accessToken(),
                                        authResponse.refreshToken()))));
    }

    private AbstractOAuth2Handler resolveHandler(final String provider) {
        return switch (provider.toLowerCase()) {
            case "google" -> googleOAuth2Handler;
            case "kakao" -> kakaoOAuth2Handler;
            default -> throw new BusinessException(ErrorCode.AUTH_006);
        };
    }
}
