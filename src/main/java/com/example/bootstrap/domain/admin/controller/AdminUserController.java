package com.example.bootstrap.domain.admin.controller;

import com.example.bootstrap.domain.admin.dto.AdminUserResponse;
import com.example.bootstrap.domain.admin.service.AdminUserService;
import com.example.bootstrap.global.response.ApiResponse;
import com.example.bootstrap.global.response.PageResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 사용자 조회 API (ADMIN-01/02).
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
}
