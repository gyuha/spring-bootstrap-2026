package com.example.bootstrap.account.domain.repository;

import com.example.bootstrap.account.domain.model.Account;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 계정 R2DBC 리포지토리.
 *
 * <p>이메일 기반 조회를 지원합니다.
 */
@Repository
public interface AccountRepository extends ReactiveCrudRepository<Account, Long> {

    /**
     * 이메일로 계정을 조회합니다.
     *
     * @param email 이메일 주소
     * @return 계정 (없으면 empty Mono)
     */
    Mono<Account> findByEmail(String email);
}
