package com.example.bootstrap.account.domain.repository;

import com.example.bootstrap.account.domain.model.SocialAccount;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * 소셜 계정 R2DBC 리포지토리.
 *
 * <p>provider 및 provider ID 기반 조회를 지원합니다.
 */
@Repository
public interface SocialAccountRepository extends ReactiveCrudRepository<SocialAccount, Long> {

    /**
     * provider와 provider ID로 소셜 계정을 조회합니다.
     *
     * @param provider   OAuth2 provider ("google" 또는 "kakao")
     * @param providerId provider 측 고유 사용자 ID
     * @return 소셜 계정 (없으면 empty Mono)
     */
    Mono<SocialAccount> findByProviderAndProviderId(String provider, String providerId);
}
