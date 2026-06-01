package com.example.bootstrap.domain.admin.controller;

import com.example.bootstrap.domain.admin.dto.AdminUserResponse;
import com.example.bootstrap.domain.admin.dto.PasswordResetResponse;
import com.example.bootstrap.domain.admin.service.AdminUserService;
import com.example.bootstrap.global.response.ApiResponse;
import com.example.bootstrap.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 사용자 조회/관리 API (ADMIN-01~04).
 *
 * <p>경로 prefix {@code /admin/**}는 SecurityConfig에서 {@code hasRole("ADMIN")}로 보호된다(D-44).
 */
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<PageResponse<AdminUserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminUserService.findPage(page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminUserResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(adminUserService.findOne(id));
    }

    /**
     * 패스워드 리셋 (ADMIN-03). 매 호출이 새 임시 PW를 생성하는 상태 변경·비멱등 연산이라 POST를
     * 채택한다(D-66). 임시 평문은 응답으로 1회만 반환된다(D-52).
     */
    @PostMapping("/{id}/password-reset")
    public ApiResponse<PasswordResetResponse> resetPassword(@PathVariable UUID id) {
        return ApiResponse.ok(adminUserService.resetPassword(id));
    }

    /** soft delete (ADMIN-04). {@code deleted_at}만 기록하고 204를 반환한다(D-55). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        adminUserService.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
