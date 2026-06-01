package com.example.bootstrap.domain.user.entity;

import com.example.bootstrap.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 엔티티 (D-31/32/33/34).
 *
 * <p>{@link BaseEntity}를 상속해 UUIDv7 PK, audit 시각(UTC), soft-delete, 낙관적 락을 자동으로 얻는다.
 *
 * <p>email 유니크는 DB 부분 유니크 인덱스({@code ux_users_email_active WHERE deleted_at IS NULL})가
 * 소유한다. 엔티티에 {@code @Column(unique = true)}를 두지 않는 이유는, Hibernate
 * {@code ddl-auto=validate}가 일반 유니크 제약을 요구해 부분 인덱스와 충돌할 위험을 피하기 위함이다
 * (A4/D-34). 부분 유니크는 활성 사용자만 email 유니크를 보장하고, 삭제 사용자 email 재가입을
 * 허용한다(Phase 3 ADMIN-04 선반영).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt 해시만 저장 (D-32). 평문/로그 노출 금지.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    private User(String email, String passwordHash, Role role) {
        this.email = email;
        this.password = passwordHash;
        this.role = role;
    }

    /**
     * 가입 시 사용 — role은 기본 {@link Role#USER} (D-33).
     *
     * @param email        회원 email
     * @param passwordHash BCrypt 해시(평문 아님 — D-32)
     */
    public static User create(String email, String passwordHash) {
        return new User(email, passwordHash, Role.USER);
    }

    /**
     * 비밀번호를 교체한다 — 인자는 BCrypt 해시만 받는다(평문 금지, D-54). 캡슐화를 위해 setter 대신
     * 도메인 메서드로 노출한다. JPA dirty checking이 UPDATE를 발행하고, {@code @Version} 낙관적
     * 락(BaseEntity)이 동시 변경을 보호한다.
     *
     * @param passwordHash 새 BCrypt 해시(평문 아님)
     */
    public void changePassword(String passwordHash) {
        this.password = passwordHash;
    }
}
