package com.example.bootstrap.domain.auth.support;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인가(403/200) 검증 전용 ADMIN 엔드포인트 — test scope (I-3).
 *
 * <p>{@code @PreAuthorize("hasRole('ADMIN')")}는 02-03의 {@code @EnableMethodSecurity}에 의존한다.
 * 컴포넌트 스캔 모호성을 없애기 위해 자동 스캔 대상이 아니며, 테스트에서
 * {@code @Import(AdminOnlyTestController.class)}로 명시 로딩한다. 미로딩 시 404가 되어 ADMIN
 * 200 단언이 공허해지므로 반드시 @Import로 컨텍스트에 등록한다.
 */
@RestController
public class AdminOnlyTestController {

    @GetMapping("/test/admin-only")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminOnly() {
        return "admin-ok";
    }
}
