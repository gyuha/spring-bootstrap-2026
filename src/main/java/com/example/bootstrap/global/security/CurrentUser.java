package com.example.bootstrap.global.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 현재 인증 주체에서 userId(UUID)·ADMIN 여부를 추출하는 공유 정적 유틸 (D-75).
 *
 * <p>JWT subject=userId 규약({@code JwtTokenProvider})에 따라 {@link Authentication#getName()}이
 * userId 문자열이며, {@code role} claim은 {@code ROLE_USER}/{@code ROLE_ADMIN} authority로 매핑된다
 * ({@code SecurityConfig.jwtAuthenticationConverter}). post/comment 소유권 체크·작성자 식별·리액션
 * user_id 세팅 등 02/03/04 모든 슬라이스가 공유한다.
 */
public final class CurrentUser {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private CurrentUser() {
    }

    /** 현재 인증 주체의 userId(JWT subject). 인증되지 않았으면 예외. */
    public static UUID userId() {
        return UUID.fromString(authentication().getName());
    }

    /** 현재 주체가 {@code ROLE_ADMIN} authority를 보유하는지 여부. */
    public static boolean isAdmin() {
        return authentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ROLE_ADMIN::equals);
    }

    private static Authentication authentication() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("인증된 사용자가 없습니다");
        }
        return authentication;
    }
}
