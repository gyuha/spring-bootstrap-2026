package com.anchors.baseline.identity.domain.model;

import com.anchors.baseline.identity.domain.exception.InvalidStateTransition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * User 애그리거트 순수 단위 테스트 — Spring/Testcontainers 미사용(도메인 객체 직접 생성).
 * 전이·멱등성·필드 소유권 불변식(IDEN-02/03/06)과 disable 멱등성(Open Q1)을 검증한다.
 * 이벤트 발행 단언은 save() 시점에 일어나므로 통합 테스트(UserLifecycleIT) 소관.
 */
class UserTest {

    /**
     * IDEN-02: invite 후 linkIdentity → ACTIVE, externalId·displayName 갱신.
     */
    @Test
    void linkIdentityTransitionsToActive() {
        User user = User.invite(new Email("a@x.com"));

        user.linkIdentity("oid-1", "홍길동");

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getExternalId()).isEqualTo("oid-1");
        assertThat(user.getDisplayName()).isEqualTo("홍길동");
    }

    /**
     * IDEN-03: 동일 신원 두 번 연결 → InvalidStateTransition (멱등성 가드).
     */
    @Test
    void linkIdentityTwiceIsRejected() {
        User user = User.invite(new Email("a@x.com"));
        user.linkIdentity("oid-1", "홍길동");

        assertThatThrownBy(() -> user.linkIdentity("oid-2", "다른이름"))
                .isInstanceOf(InvalidStateTransition.class);
    }

    /**
     * IDEN-06: linkIdentity 는 관리자 입력 필드(internalNote)를 인자로 받지 않아
     * 구조적으로 보존되고, IdP 출처 필드(displayName)만 갱신된다.
     */
    @Test
    void linkIdentityPreservesInternalNoteAndUpdatesDisplayName() {
        User user = User.invite(new Email("a@x.com"));
        user.updateInternalNote("관리자 메모");

        user.linkIdentity("oid-1", "홍길동");

        assertThat(user.getInternalNote()).isEqualTo("관리자 메모");
        assertThat(user.getDisplayName()).isEqualTo("홍길동");
    }

    /**
     * disable 정상 전이 → DISABLED.
     */
    @Test
    void disableTransitionsToDisabled() {
        User user = User.invite(new Email("a@x.com"));

        user.disable();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    /**
     * Open Q1: disable 두 번 호출 → 예외 없이 DISABLED 유지(무연산 멱등성).
     */
    @Test
    void disableTwiceIsIdempotent() {
        User user = User.invite(new Email("a@x.com"));
        user.disable();

        user.disable();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    /**
     * D-02: Email 은 생성 시점 형식 검증 — 잘못된 입력은 예외, 유효한 입력은 통과.
     */
    @Test
    void emailValidatesFormatAtConstruction() {
        assertThatThrownBy(() -> new Email("not-an-email"))
                .isInstanceOf(IllegalArgumentException.class);

        Email valid = new Email("a@x.com");
        assertThat(valid.getValue()).isEqualTo("a@x.com");
    }
}
