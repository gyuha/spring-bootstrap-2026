package com.example.bootstrap.account.domain.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Refresh Token 엔티티.
 *
 * <p>{@code refresh_tokens} 테이블에 매핑됩니다.
 * Rotation 방식으로 관리되며 만료 토큰은 Batch Job이 일괄 삭제합니다.
 */
@Table("refresh_tokens")
public class RefreshToken {

    @Id
    private Long id;

    private Long userId;

    private String token;

    private LocalDateTime expiredAt;

    @CreatedDate
    private LocalDateTime createdAt;

    /** 기본 생성자. Spring Data R2DBC가 내부적으로 사용합니다. */
    public RefreshToken() {
    }

    /**
     * PK를 반환합니다.
     *
     * @return Refresh Token PK
     */
    public Long getId() {
        return id;
    }

    /**
     * PK를 설정합니다.
     *
     * @param id Refresh Token PK
     */
    public void setId(final Long id) {
        this.id = id;
    }

    /**
     * 계정 ID를 반환합니다.
     *
     * @return Account FK
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 계정 ID를 설정합니다.
     *
     * @param userId Account FK
     */
    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    /**
     * Refresh Token 값을 반환합니다.
     *
     * @return JWT Refresh Token 문자열
     */
    public String getToken() {
        return token;
    }

    /**
     * Refresh Token 값을 설정합니다.
     *
     * @param token JWT Refresh Token 문자열
     */
    public void setToken(final String token) {
        this.token = token;
    }

    /**
     * 만료 일시를 반환합니다.
     *
     * @return 만료 일시 (발급 후 14일)
     */
    public LocalDateTime getExpiredAt() {
        return expiredAt;
    }

    /**
     * 만료 일시를 설정합니다.
     *
     * @param expiredAt 만료 일시
     */
    public void setExpiredAt(final LocalDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    /**
     * 발급 일시를 반환합니다.
     *
     * @return 발급 일시 (UTC)
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 발급 일시를 설정합니다.
     *
     * @param createdAt 발급 일시
     */
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
