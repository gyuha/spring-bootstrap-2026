package com.example.bootstrap.account.application.service;

import com.example.bootstrap.account.application.dto.AccountResponse;
import com.example.bootstrap.account.application.dto.RegisterRequest;
import com.example.bootstrap.account.application.dto.UpdateProfileRequest;
import com.example.bootstrap.account.domain.model.Account;
import com.example.bootstrap.account.domain.repository.AccountRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 계정 도메인 애플리케이션 서비스.
 *
 * <p>회원 가입, 조회, 프로필 수정, 탈퇴 등 계정 관련 핵심 비즈니스 로직을 담당합니다.
 * 모든 작업은 리액티브 스트림({@link Mono})으로 반환되며
 * 이벤트 루프를 블로킹하는 코드를 포함하지 않습니다.
 */
@Service
public class AccountService {

    private static final String DEFAULT_ROLE = "USER";

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * AccountService 생성자.
     *
     * @param accountRepository 계정 R2DBC 리포지토리
     * @param passwordEncoder   BCrypt 비밀번호 인코더
     */
    public AccountService(
            final AccountRepository accountRepository,
            final PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 신규 계정을 등록합니다.
     *
     * <p>이메일 중복 검증 후 BCrypt 인코딩된 비밀번호로 계정을 저장합니다.
     * 가입 즉시 {@code emailVerified}가 {@code true}로 설정됩니다.
     *
     * @param request 회원 가입 요청 DTO
     * @return 생성된 계정 응답 DTO
     * @throws BusinessException 이메일이 이미 존재하는 경우 {@link ErrorCode#ACCOUNT_001}
     */
    public Mono<AccountResponse> register(final RegisterRequest request) {
        return accountRepository.findByEmail(request.email())
                .flatMap(existing -> Mono.<Account>error(
                        new BusinessException(ErrorCode.ACCOUNT_001)))
                .switchIfEmpty(Mono.defer(() -> {
                    Account account = buildNewAccount(request);
                    return accountRepository.save(account);
                }))
                .map(AccountResponse::from);
    }

    /**
     * ID로 계정을 조회합니다.
     *
     * @param id 계정 PK
     * @return 계정 응답 DTO
     * @throws BusinessException 계정이 존재하지 않는 경우 {@link ErrorCode#ACCOUNT_002}
     */
    public Mono<AccountResponse> findById(final Long id) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ACCOUNT_002)))
                .map(AccountResponse::from);
    }

    /**
     * 계정 프로필을 수정합니다.
     *
     * <p>닉네임과 프로필 이미지 URL을 갱신합니다.
     *
     * @param id      수정할 계정 PK
     * @param request 프로필 업데이트 요청 DTO
     * @return 수정된 계정 응답 DTO
     * @throws BusinessException 계정이 존재하지 않는 경우 {@link ErrorCode#ACCOUNT_002}
     */
    public Mono<AccountResponse> updateProfile(final Long id, final UpdateProfileRequest request) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ACCOUNT_002)))
                .flatMap(account -> {
                    account.setNickname(request.nickname());
                    account.setProfileImageUrl(request.profileImageUrl());
                    return accountRepository.save(account);
                })
                .map(AccountResponse::from);
    }

    /**
     * 계정을 삭제합니다.
     *
     * @param id 삭제할 계정 PK
     * @return 완료 시그널
     * @throws BusinessException 계정이 존재하지 않는 경우 {@link ErrorCode#ACCOUNT_002}
     */
    public Mono<Void> delete(final Long id) {
        return accountRepository.findById(id)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.ACCOUNT_002)))
                .flatMap(accountRepository::delete);
    }

    /**
     * 회원 가입 요청으로부터 신규 Account 엔티티를 초기화합니다.
     *
     * @param request 회원 가입 요청 DTO
     * @return 초기화된 {@link Account} 엔티티
     */
    private Account buildNewAccount(final RegisterRequest request) {
        Account account = new Account();
        account.setEmail(request.email());
        account.setPassword(passwordEncoder.encode(request.password()));
        account.setNickname(request.nickname());
        account.setRole(DEFAULT_ROLE);
        account.setEmailVerified(true);
        return account;
    }
}
