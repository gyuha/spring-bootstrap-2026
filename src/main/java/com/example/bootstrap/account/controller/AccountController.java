package com.example.bootstrap.account.controller;

import com.example.bootstrap.account.application.dto.AccountResponse;
import com.example.bootstrap.account.application.dto.UpdateProfileRequest;
import com.example.bootstrap.account.application.service.AccountService;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 계정 API 컨트롤러.
 *
 * <p>인증된 사용자의 내 정보 조회, 프로필 수정, 회원 탈퇴 엔드포인트를 제공합니다.
 * 모든 엔드포인트는 JWT Bearer 토큰 인증이 필요하며 {@code /api/v1/accounts/me} 경로로 노출됩니다.
 *
 * <p>{@link Authentication#getPrincipal()}에서 userId(Long)를 추출합니다.
 * userId는 {@code JwtAuthenticationFilter}가 토큰 파싱 후 설정합니다.
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    /**
     * @param accountService 계정 CRUD 서비스
     */
    public AccountController(final AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * 인증된 사용자의 계정 정보를 조회합니다.
     *
     * @param authentication JWT 인증 정보 (principal = userId Long)
     * @return 200 OK + 계정 응답 DTO
     * @throws BusinessException 계정이 존재하지 않는 경우 {@link ErrorCode#ACCOUNT_002}
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<ApiResponse<AccountResponse>>> getMe(
            final Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return accountService.findById(userId)
                .map(response -> ResponseEntity.ok(
                        ApiResponse.success("계정 정보를 조회했습니다.", response)));
    }

    /**
     * 인증된 사용자의 프로필을 수정합니다.
     *
     * @param authentication JWT 인증 정보 (principal = userId Long)
     * @param request        프로필 수정 요청
     * @return 200 OK + 수정된 계정 응답 DTO
     * @throws BusinessException 계정이 존재하지 않는 경우 {@link ErrorCode#ACCOUNT_002}
     */
    @PutMapping("/me")
    public Mono<ResponseEntity<ApiResponse<AccountResponse>>> updateMe(
            final Authentication authentication,
            @Valid @RequestBody final UpdateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return accountService.updateProfile(userId, request)
                .map(response -> ResponseEntity.ok(
                        ApiResponse.success("프로필이 수정되었습니다.", response)));
    }

    /**
     * 인증된 사용자의 계정을 탈퇴 처리합니다.
     *
     * @param authentication JWT 인증 정보 (principal = userId Long)
     * @return 200 OK
     * @throws BusinessException 계정이 존재하지 않는 경우 {@link ErrorCode#ACCOUNT_002}
     */
    @DeleteMapping("/me")
    public Mono<ResponseEntity<ApiResponse<Void>>> deleteMe(
            final Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return accountService.delete(userId)
                .thenReturn(ResponseEntity.<ApiResponse<Void>>ok(
                        ApiResponse.success("회원 탈퇴가 완료되었습니다.")));
    }
}
