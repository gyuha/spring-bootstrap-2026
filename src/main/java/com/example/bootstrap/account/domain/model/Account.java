package com.example.bootstrap.account.domain.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * 사용자 계정 엔티티.
 *
 * <p>이메일/비밀번호 기반 및 OAuth2 소셜 로그인 계정을 모두 표현합니다.
 * OAuth2 전용 계정은 {@code password} 필드가 {@code null}입니다.
 */
@Table("users")
public class Account {

    @Id
    private Long id;

    private String email;

    /** BCrypt 인코딩된 비밀번호. OAuth2 전용 계정은 null. */
    private String password;

    private String nickname;

    private String role;

    private boolean emailVerified;

    private String profileImageUrl;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /** 기본 생성자. Spring Data R2DBC가 내부적으로 사용합니다. */
    public Account() {
    }

    /**
     * 계정 ID를 반환합니다.
     *
     * @return account PK
     */
    public Long getId() {
        return id;
    }

    /**
     * 계정 ID를 설정합니다.
     *
     * @param id account PK
     */
    public void setId(final Long id) {
        this.id = id;
    }

    /**
     * 이메일 주소를 반환합니다.
     *
     * @return 이메일
     */
    public String getEmail() {
        return email;
    }

    /**
     * 이메일 주소를 설정합니다.
     *
     * @param email 이메일
     */
    public void setEmail(final String email) {
        this.email = email;
    }

    /**
     * 비밀번호(BCrypt)를 반환합니다.
     *
     * @return BCrypt 비밀번호, OAuth2 전용 계정은 null
     */
    public String getPassword() {
        return password;
    }

    /**
     * 비밀번호를 설정합니다.
     *
     * @param password BCrypt 인코딩된 비밀번호
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * 닉네임을 반환합니다.
     *
     * @return 닉네임
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 닉네임을 설정합니다.
     *
     * @param nickname 닉네임
     */
    public void setNickname(final String nickname) {
        this.nickname = nickname;
    }

    /**
     * 권한 역할을 반환합니다.
     *
     * @return "USER" 또는 "ADMIN"
     */
    public String getRole() {
        return role;
    }

    /**
     * 권한 역할을 설정합니다.
     *
     * @param role "USER" 또는 "ADMIN"
     */
    public void setRole(final String role) {
        this.role = role;
    }

    /**
     * 이메일 인증 여부를 반환합니다.
     *
     * @return 인증 여부 (가입 즉시 true)
     */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    /**
     * 이메일 인증 여부를 설정합니다.
     *
     * @param emailVerified 인증 여부
     */
    public void setEmailVerified(final boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    /**
     * 프로필 이미지 URL을 반환합니다.
     *
     * @return 프로필 이미지 URL (nullable)
     */
    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    /**
     * 프로필 이미지 URL을 설정합니다.
     *
     * @param profileImageUrl 프로필 이미지 URL
     */
    public void setProfileImageUrl(final String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    /**
     * 생성일시를 반환합니다.
     *
     * @return 생성일시 (UTC)
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 생성일시를 설정합니다.
     *
     * @param createdAt 생성일시
     */
    public void setCreatedAt(final LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 수정일시를 반환합니다.
     *
     * @return 수정일시 (UTC)
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 수정일시를 설정합니다.
     *
     * @param updatedAt 수정일시
     */
    public void setUpdatedAt(final LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
