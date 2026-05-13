package com.example.bootstrap.account.domain.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * OAuth2 소셜 계정 연동 엔티티.
 *
 * <p>{@code oauth_accounts} 테이블에 매핑됩니다.
 * 하나의 {@link Account}는 여러 소셜 계정을 가질 수 있습니다 (1:N).
 */
@Table("oauth_accounts")
public class SocialAccount {

    @Id
    private Long id;

    private Long userId;

    /** OAuth2 provider 식별자 (google 또는 kakao). */
    private String provider;

    /** provider 측 고유 사용자 ID. */
    private String providerId;

    @CreatedDate
    private LocalDateTime createdAt;

    /** 기본 생성자. Spring Data R2DBC가 내부적으로 사용합니다. */
    public SocialAccount() {
    }

    /**
     * PK를 반환합니다.
     *
     * @return 소셜 계정 PK
     */
    public Long getId() {
        return id;
    }

    /**
     * PK를 설정합니다.
     *
     * @param id 소셜 계정 PK
     */
    public void setId(final Long id) {
        this.id = id;
    }

    /**
     * 연결된 계정 ID를 반환합니다.
     *
     * @return Account FK
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 연결된 계정 ID를 설정합니다.
     *
     * @param userId Account FK
     */
    public void setUserId(final Long userId) {
        this.userId = userId;
    }

    /**
     * OAuth2 provider 식별자를 반환합니다.
     *
     * @return "google" 또는 "kakao"
     */
    public String getProvider() {
        return provider;
    }

    /**
     * OAuth2 provider 식별자를 설정합니다.
     *
     * @param provider "google" 또는 "kakao"
     */
    public void setProvider(final String provider) {
        this.provider = provider;
    }

    /**
     * provider 측 고유 사용자 ID를 반환합니다.
     *
     * @return provider 사용자 ID
     */
    public String getProviderId() {
        return providerId;
    }

    /**
     * provider 측 고유 사용자 ID를 설정합니다.
     *
     * @param providerId provider 사용자 ID
     */
    public void setProviderId(final String providerId) {
        this.providerId = providerId;
    }

    /**
     * 연동 일시를 반환합니다.
     *
     * @return 연동 일시 (UTC)
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 연동 일시를 설정합니다.
     *
     * @param createdAt 연동 일시
     */
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
