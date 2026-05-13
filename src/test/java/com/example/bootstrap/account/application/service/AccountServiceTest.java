package com.example.bootstrap.account.application.service;

import com.example.bootstrap.account.application.dto.RegisterRequest;
import com.example.bootstrap.account.application.dto.UpdateProfileRequest;
import com.example.bootstrap.account.domain.model.Account;
import com.example.bootstrap.account.domain.repository.AccountRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AccountService} 단위 테스트.
 *
 * <p>{@link AccountRepository}와 {@link PasswordEncoder}는 Mockito로 대체됩니다.
 * 리액티브 체인은 Project Reactor의 {@link StepVerifier}로 검증됩니다.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "plain-password";
    private static final String TEST_ENCODED_PASSWORD = "$2a$10$encodedPasswordHash";
    private static final String TEST_NICKNAME = "TestUser";

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, passwordEncoder);
    }

    // ── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: 이메일 중복 없으면 계정을 저장하고 AccountResponse를 반환한다")
    void register_whenEmailNotTaken_savesAccountAndReturnsResponse() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Account saved = buildAccount(1L, TEST_EMAIL, TEST_NICKNAME);

        when(accountRepository.findByEmail(TEST_EMAIL)).thenReturn(Mono.empty());
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_ENCODED_PASSWORD);
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.register(request))
                .assertNext(response -> {
                    assertThat(response.id()).isEqualTo(1L);
                    assertThat(response.email()).isEqualTo(TEST_EMAIL);
                    assertThat(response.nickname()).isEqualTo(TEST_NICKNAME);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("register: 이메일 중복 시 BusinessException(ACCOUNT_001)을 발생시킨다")
    void register_whenEmailAlreadyExists_throwsAccount001() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Account existing = buildAccount(99L, TEST_EMAIL, "OtherUser");

        when(accountRepository.findByEmail(TEST_EMAIL)).thenReturn(Mono.just(existing));

        StepVerifier.create(accountService.register(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_001);
                })
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: 저장되는 Account의 비밀번호는 BCrypt 인코딩된 값이다")
    void register_whenRegistering_passwordIsBcryptEncoded() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Account saved = buildAccount(2L, TEST_EMAIL, TEST_NICKNAME);

        when(accountRepository.findByEmail(TEST_EMAIL)).thenReturn(Mono.empty());
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn(TEST_ENCODED_PASSWORD);
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.register(request))
                .assertNext(response -> assertThat(response.id()).isEqualTo(2L))
                .verifyComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo(TEST_ENCODED_PASSWORD);
    }

    @Test
    @DisplayName("register: 저장되는 Account의 role은 USER이고 emailVerified는 true이다")
    void register_whenRegistering_accountHasUserRoleAndEmailVerifiedTrue() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Account saved = buildAccount(3L, TEST_EMAIL, TEST_NICKNAME);

        when(accountRepository.findByEmail(TEST_EMAIL)).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn(TEST_ENCODED_PASSWORD);
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.register(request))
                .assertNext(response -> assertThat(response.id()).isEqualTo(3L))
                .verifyComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
        assertThat(captor.getValue().isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("register: 저장되는 Account의 이메일과 닉네임이 요청 값과 일치한다")
    void register_whenRegistering_accountEmailAndNicknameMatchRequest() {
        RegisterRequest request = new RegisterRequest(TEST_EMAIL, TEST_PASSWORD, TEST_NICKNAME);
        Account saved = buildAccount(4L, TEST_EMAIL, TEST_NICKNAME);

        when(accountRepository.findByEmail(TEST_EMAIL)).thenReturn(Mono.empty());
        when(passwordEncoder.encode(anyString())).thenReturn(TEST_ENCODED_PASSWORD);
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.register(request))
                .assertNext(response -> assertThat(response.id()).isEqualTo(4L))
                .verifyComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(TEST_EMAIL);
        assertThat(captor.getValue().getNickname()).isEqualTo(TEST_NICKNAME);
    }

    // ── findById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById: 계정이 존재하면 AccountResponse를 반환한다")
    void findById_whenAccountExists_returnsAccountResponse() {
        Account account = buildAccount(10L, "user@example.com", "User");

        when(accountRepository.findById(10L)).thenReturn(Mono.just(account));

        StepVerifier.create(accountService.findById(10L))
                .assertNext(response -> {
                    assertThat(response.id()).isEqualTo(10L);
                    assertThat(response.email()).isEqualTo("user@example.com");
                    assertThat(response.nickname()).isEqualTo("User");
                    assertThat(response.role()).isEqualTo("USER");
                    assertThat(response.emailVerified()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findById: 계정이 존재하지 않으면 BusinessException(ACCOUNT_002)을 발생시킨다")
    void findById_whenAccountNotFound_throwsAccount002() {
        when(accountRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(accountService.findById(999L))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_002);
                })
                .verify();
    }

    @Test
    @DisplayName("findById: AccountResponse에 계정의 모든 필드가 올바르게 매핑된다")
    void findById_whenAccountExists_allFieldsMappedCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        Account account = buildAccount(20L, "admin@example.com", "Admin");
        account.setRole("ADMIN");
        account.setProfileImageUrl("https://example.com/photo.jpg");
        account.setCreatedAt(now);
        account.setUpdatedAt(now);

        when(accountRepository.findById(20L)).thenReturn(Mono.just(account));

        StepVerifier.create(accountService.findById(20L))
                .assertNext(response -> {
                    assertThat(response.id()).isEqualTo(20L);
                    assertThat(response.role()).isEqualTo("ADMIN");
                    assertThat(response.profileImageUrl()).isEqualTo("https://example.com/photo.jpg");
                    assertThat(response.createdAt()).isEqualTo(now);
                    assertThat(response.updatedAt()).isEqualTo(now);
                })
                .verifyComplete();
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile: 계정이 존재하면 닉네임과 프로필 이미지를 수정하고 응답을 반환한다")
    void updateProfile_whenAccountExists_updatesFieldsAndReturnsResponse() {
        Account existing = buildAccount(30L, TEST_EMAIL, "OldNickname");
        Account updated = buildAccount(30L, TEST_EMAIL, "NewNickname");
        updated.setProfileImageUrl("https://example.com/new.jpg");
        UpdateProfileRequest request = new UpdateProfileRequest("NewNickname",
                "https://example.com/new.jpg");

        when(accountRepository.findById(30L)).thenReturn(Mono.just(existing));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(updated));

        StepVerifier.create(accountService.updateProfile(30L, request))
                .assertNext(response -> {
                    assertThat(response.nickname()).isEqualTo("NewNickname");
                    assertThat(response.profileImageUrl()).isEqualTo("https://example.com/new.jpg");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("updateProfile: 계정이 존재하지 않으면 BusinessException(ACCOUNT_002)을 발생시킨다")
    void updateProfile_whenAccountNotFound_throwsAccount002() {
        UpdateProfileRequest request = new UpdateProfileRequest("NewNick", null);

        when(accountRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(accountService.updateProfile(999L, request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_002);
                })
                .verify();

        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateProfile: 저장 시 Account에 새 닉네임과 프로필 이미지 URL이 설정된다")
    void updateProfile_whenUpdating_savesNewNicknameAndProfileImageUrl() {
        Account existing = buildAccount(40L, TEST_EMAIL, "OldNick");
        Account saved = buildAccount(40L, TEST_EMAIL, "UpdatedNick");
        saved.setProfileImageUrl("https://cdn.example.com/avatar.png");
        UpdateProfileRequest request = new UpdateProfileRequest(
                "UpdatedNick", "https://cdn.example.com/avatar.png");

        when(accountRepository.findById(40L)).thenReturn(Mono.just(existing));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.updateProfile(40L, request))
                .assertNext(response -> assertThat(response.id()).isEqualTo(40L))
                .verifyComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("UpdatedNick");
        assertThat(captor.getValue().getProfileImageUrl())
                .isEqualTo("https://cdn.example.com/avatar.png");
    }

    @Test
    @DisplayName("updateProfile: profileImageUrl이 null이어도 정상 수정된다")
    void updateProfile_whenProfileImageUrlIsNull_updateSucceeds() {
        Account existing = buildAccount(50L, TEST_EMAIL, "Nickname");
        Account saved = buildAccount(50L, TEST_EMAIL, "NewNickname");
        UpdateProfileRequest request = new UpdateProfileRequest("NewNickname", null);

        when(accountRepository.findById(50L)).thenReturn(Mono.just(existing));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(saved));

        StepVerifier.create(accountService.updateProfile(50L, request))
                .assertNext(response -> assertThat(response.nickname()).isEqualTo("NewNickname"))
                .verifyComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(captor.capture());
        assertThat(captor.getValue().getProfileImageUrl()).isNull();
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete: 계정이 존재하면 삭제 후 완료 시그널을 반환한다")
    void delete_whenAccountExists_deletesAndCompletes() {
        Account account = buildAccount(60L, TEST_EMAIL, TEST_NICKNAME);

        when(accountRepository.findById(60L)).thenReturn(Mono.just(account));
        when(accountRepository.delete(account)).thenReturn(Mono.empty());

        StepVerifier.create(accountService.delete(60L))
                .verifyComplete();

        verify(accountRepository).delete(account);
    }

    @Test
    @DisplayName("delete: 계정이 존재하지 않으면 BusinessException(ACCOUNT_002)을 발생시킨다")
    void delete_whenAccountNotFound_throwsAccount002() {
        when(accountRepository.findById(999L)).thenReturn(Mono.empty());

        StepVerifier.create(accountService.delete(999L))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.ACCOUNT_002);
                })
                .verify();

        verify(accountRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: 삭제 시 올바른 Account 엔티티를 Repository에 전달한다")
    void delete_whenDeleting_passesCorrectAccountToRepository() {
        Account account = buildAccount(70L, "delete@example.com", "DeleteMe");

        when(accountRepository.findById(70L)).thenReturn(Mono.just(account));
        when(accountRepository.delete(any(Account.class))).thenReturn(Mono.empty());

        StepVerifier.create(accountService.delete(70L))
                .verifyComplete();

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(70L);
        assertThat(captor.getValue().getEmail()).isEqualTo("delete@example.com");
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    private static Account buildAccount(final Long id, final String email, final String nickname) {
        Account account = new Account();
        account.setId(id);
        account.setEmail(email);
        account.setNickname(nickname);
        account.setRole("USER");
        account.setEmailVerified(true);
        return account;
    }
}
