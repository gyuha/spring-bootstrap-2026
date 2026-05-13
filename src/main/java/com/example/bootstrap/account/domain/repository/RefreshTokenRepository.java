package com.example.bootstrap.account.domain.repository;

import com.example.bootstrap.account.domain.model.RefreshToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Refresh Token R2DBC 리포지토리.
 *
 * <p>토큰 조회, 삭제, 만료 토큰 일괄 조회를 지원합니다.
 */
@Repository
public interface RefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, Long> {

    /**
     * 토큰 값으로 Refresh Token을 조회합니다.
     *
     * @param token JWT Refresh Token 문자열
     * @return Refresh Token (없으면 empty Mono)
     */
    Mono<RefreshToken> findByToken(String token);

    /**
     * 특정 사용자의 모든 Refresh Token을 삭제합니다.
     *
     * @param userId Account PK
     * @return 완료 시그널
     */
    Mono<Void> deleteByUserId(Long userId);

    /**
     * 주어진 일시 이전에 만료된 Refresh Token 목록을 반환합니다.
     *
     * <p>Batch Job에서 만료 토큰 정리에 사용합니다.
     *
     * @param dateTime 기준 일시
     * @return 만료된 토큰 목록
     */
    Flux<RefreshToken> findByExpiredAtBefore(LocalDateTime dateTime);
}
