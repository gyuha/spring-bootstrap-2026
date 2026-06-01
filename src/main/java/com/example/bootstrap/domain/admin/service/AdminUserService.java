package com.example.bootstrap.domain.admin.service;

import com.example.bootstrap.domain.admin.dto.AdminUserResponse;
import com.example.bootstrap.domain.admin.dto.PasswordResetResponse;
import com.example.bootstrap.domain.admin.mapper.UserQueryMapper;
import com.example.bootstrap.domain.auth.service.RefreshTokenStore;
import com.example.bootstrap.domain.user.entity.User;
import com.example.bootstrap.domain.user.repository.UserRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.response.PageResponse;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 사용자 조회 비즈니스 로직 + 트랜잭션 경계 (ADMIN-01/02, D-08).
 *
 * <p>목록 페이징은 MyBatis {@link UserQueryMapper}(D-46/47), 단건은 JPA
 * {@link UserRepository#findById}({@code @SQLRestriction}이 soft-delete 자동 제외, D-50)로
 * 처리한다. 둘은 단일 JpaTransactionManager를 공유한다.
 *
 * <p>패스워드 리셋(ADMIN-03)은 서버 생성 임시 평문을 BCrypt 해싱해 저장하고 평문을 1회 반환하며,
 * 해당 사용자 Refresh를 전량 무효화한다(D-51~54). soft delete(ADMIN-04)는 {@code user.softDelete()}로
 * {@code deleted_at}만 기록한다(D-55).
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UserQueryMapper userQueryMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> findPage(int page, int size) {
        long offset = (long) page * size;
        return PageResponse.of(
                userQueryMapper.findPage(offset, size),
                userQueryMapper.countActive(),
                page,
                size);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse findOne(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new AdminUserResponse(
                user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }

    /**
     * 패스워드 리셋 (ADMIN-03, D-51~53). 서버가 임시 평문을 생성→BCrypt 해싱해 저장하고, 평문을
     * 1회 반환한다. 직후 해당 사용자 Refresh를 전량 무효화한다. 기존 Access는 짧은 TTL로 자연
     * 만료에 맡기며 블랙리스트하지 않는다(D-53). 평문은 응답 외 어디에도 저장하지 않는다(D-52).
     *
     * @param id 대상 사용자 식별자
     * @return 임시 평문 패스워드 1회 응답
     * @throws BusinessException 미존재/삭제 사용자({@link ErrorCode#USER_NOT_FOUND})
     */
    @Transactional
    public PasswordResetResponse resetPassword(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String temporary = newTemporaryPassword();
        user.changePassword(passwordEncoder.encode(temporary));
        refreshTokenStore.invalidateAll(id);
        return new PasswordResetResponse(temporary);
    }

    /**
     * soft delete (ADMIN-04, D-55). {@code user.softDelete()}로 {@code deleted_at}만 기록하고
     * 하드 DELETE하지 않는다. 이후 목록/카운트/단건에서 제외된다(D-56).
     *
     * @param id 대상 사용자 식별자
     * @throws BusinessException 미존재/삭제 사용자({@link ErrorCode#USER_NOT_FOUND})
     */
    @Transactional
    public void softDelete(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.softDelete();
    }

    /** SecureRandom + URL-safe Base64로 충분 엔트로피의 임시 평문을 만든다(D-51, AuthService 패턴). */
    private static String newTemporaryPassword() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
