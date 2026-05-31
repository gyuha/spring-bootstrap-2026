package com.anchors.baseline.identity.domain.model;

import com.anchors.baseline.identity.domain.event.UserDisabled;
import com.anchors.baseline.identity.domain.exception.InvalidStateTransition;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import org.springframework.data.domain.AbstractAggregateRoot;

/**
 * Identity 컨텍스트의 애그리거트 루트(JPA 방식 A, D-01).
 * 모든 상태 변경은 의미 메서드(invite/linkIdentity/disable/updateInternalNote)로만 — public setter 금지.
 * AbstractAggregateRoot 상속으로 도메인 이벤트를 누적하고 save() 시점에 발행한다(D-03).
 */
@Entity
@Table(name = "users")
@Getter
public class User extends AbstractAggregateRoot<User> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "email", nullable = false, unique = true))
    private Email email;

    // nullable String 직접 매핑 — all-null @Embeddable 함정(Pitfall 1) 회피. 연결 전 null.
    @Column(name = "external_id", unique = true)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    // IdP 출처 필드 — linkIdentity 가 갱신한다.
    @Column(name = "display_name")
    private String displayName;

    // 관리자 입력 필드(SC#4) — linkIdentity 가 인자로 받지 않아 구조적으로 보존된다.
    @Column(name = "internal_note")
    private String internalNote;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    private User(Email email) {
        this.email = email;
        this.status = UserStatus.INVITED;
    }

    /**
     * IDEN-01: 관리자가 미로그인 사용자를 이메일로 초대 → INVITED.
     */
    public static User invite(Email email) {
        return new User(email);
    }

    /**
     * IDEN-02/03/06: 최초 로그인 시 IdP 신원을 연결한다(INVITED→ACTIVE).
     * 멱등성 가드는 externalId null 여부가 아니라 status 기준(Pitfall 1 회피).
     * 관리자 입력 필드(internalNote)는 인자에 없어 구조적으로 미변경된다.
     */
    public void linkIdentity(String externalId, String idpDisplayName) {
        if (this.status != UserStatus.INVITED) {
            throw new InvalidStateTransition("이미 신원이 연결된 사용자입니다");
        }
        this.externalId = externalId;
        this.displayName = idpDisplayName;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * IDEN-04: 사용자를 비활성화한다(INVITED 초대 취소 또는 ACTIVE 해지 → DISABLED).
     * 이미 DISABLED 면 무연산(이벤트 미발행 — Open Q1 결정).
     *
     * <p>UserDisabled 이벤트는 영속화된 애그리거트(id != null)에 대해서만 발행한다.
     * 컨텍스트 간 참조는 불변 로컬 PK(§4.1)이므로 null id 페이로드를 발행하지 않는다.
     * 서비스 경로는 findById 로 로드하므로 항상 id 가 존재하며, 미영속 인메모리 애그리거트는
     * 시스템에 아직 존재하지 않으므로 발행 대상이 아니다.
     */
    public void disable() {
        if (this.status == UserStatus.DISABLED) {
            return;
        }
        this.status = UserStatus.DISABLED;
        if (this.id != null) {
            registerEvent(new UserDisabled(this.id));
        }
    }

    /**
     * 관리자 입력 메모를 설정한다(SC#4). public setter 가 아닌 의미 메서드(D-01).
     */
    public void updateInternalNote(String internalNote) {
        this.internalNote = internalNote;
    }
}
