package com.example.bootstrap.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.bootstrap.domain.admin.dto.PasswordResetResponse;
import com.example.bootstrap.domain.admin.mapper.UserQueryMapper;
import com.example.bootstrap.domain.admin.service.AdminUserService;
import com.example.bootstrap.domain.auth.service.RefreshTokenStore;
import com.example.bootstrap.domain.user.entity.User;
import com.example.bootstrap.domain.user.repository.UserRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import com.example.bootstrap.global.security.TokenBlacklist;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 패스워드 리셋/soft delete 오케스트레이션 단위 검증 (ADMIN-03/04, D-51~55).
 *
 * <p>Mockito로 협력자를 mock해 서비스의 오케스트레이션만 검증한다. 전 흐름·실 Redis 무효화는
 * 03-03 통합 테스트가 담당한다. 핵심 박제: 임시 평문은 비어있지 않고(엔트로피),
 * {@code changePassword}에 전달되는 값은 평문이 아닌 {@code encode} 결과(해시),
 * {@code RefreshTokenStore.invalidateAll(id)} 1회 호출, {@link TokenBlacklist}는 미호출(D-53),
 * 미존재/삭제 사용자는 {@link ErrorCode#USER_NOT_FOUND}.
 */
@ExtendWith(MockitoExtension.class)
class AdminPasswordResetServiceTest {

    @Mock
    UserQueryMapper userQueryMapper;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    RefreshTokenStore refreshTokenStore;

    @Mock
    TokenBlacklist tokenBlacklist;

    private AdminUserService service() {
        return new AdminUserService(
                userQueryMapper, userRepository, passwordEncoder, refreshTokenStore);
    }

    @Test
    void resetPassword_generatesTempReturnsPlaintextStoresHashAndInvalidatesRefresh() {
        UUID id = UUID.randomUUID();
        User user = User.create("user@example.com", "old-hash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(any(CharSequence.class)))
                .thenAnswer(inv -> "$2a$bcrypt$" + inv.getArgument(0));

        PasswordResetResponse response = service().resetPassword(id);

        // 임시 평문이 응답으로 1회 반환되고 비어있지 않다(엔트로피).
        assertThat(response.temporaryPassword()).isNotBlank();

        // encode에 전달된 평문 == 응답 평문, 저장된 값(changePassword 결과)은 해시.
        ArgumentCaptor<CharSequence> encoded = ArgumentCaptor.forClass(CharSequence.class);
        verify(passwordEncoder).encode(encoded.capture());
        assertThat(encoded.getValue().toString()).isEqualTo(response.temporaryPassword());
        assertThat(user.getPassword())
                .isNotEqualTo(response.temporaryPassword())
                .isEqualTo("$2a$bcrypt$" + response.temporaryPassword());

        // 해당 사용자 Refresh 전량 무효화 1회.
        verify(refreshTokenStore, times(1)).invalidateAll(id);

        // Access는 블랙리스트하지 않는다(D-53).
        verifyNoInteractions(tokenBlacklist);
    }

    @Test
    void resetPassword_missingUser_throwsUserNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().resetPassword(id))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(refreshTokenStore, never()).invalidateAll(eq(id));
        verifyNoInteractions(tokenBlacklist);
    }

    @Test
    void softDelete_marksUserDeleted() {
        UUID id = UUID.randomUUID();
        User user = User.create("user@example.com", "hash");
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        service().softDelete(id);

        assertThat(user.isDeleted()).isTrue();
    }

    @Test
    void softDelete_missingUser_throwsUserNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().softDelete(id))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }
}
