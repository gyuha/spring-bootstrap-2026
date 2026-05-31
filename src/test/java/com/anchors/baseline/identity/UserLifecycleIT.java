package com.anchors.baseline.identity;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.identity.application.EmailAlreadyExists;
import com.anchors.baseline.identity.application.IdentityApplicationService;
import com.anchors.baseline.identity.domain.event.UserDisabled;
import com.anchors.baseline.identity.domain.model.Email;
import com.anchors.baseline.identity.domain.model.User;
import com.anchors.baseline.identity.domain.model.UserStatus;
import com.anchors.baseline.identity.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Identity 컨텍스트 통합 테스트 — Testcontainers(PostgreSQL) 기반.
 *
 * <p>IdentityApplicationService 를 통해 유스케이스를 구동해 SC#1·#3·#5 를 증명한다.
 * 이벤트(UserDisabled)는 disable() 직후가 아니라 service.disable() 내부 save() 시점에
 * 트랜잭션 내·동기 발행되므로(RESEARCH Pitfall 3), service 호출 후에 단언한다.
 *
 * <p>이 IT 가 부팅된다는 사실 자체가 Hibernate 매핑(AbstractAggregateRoot 의 domainEvents
 * @Transient 포함)이 V2 스키마와 ddl-auto: validate 하에 정합함을 증명한다(A4/A5).
 */
@RecordApplicationEvents
class UserLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    IdentityApplicationService identityService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ApplicationEvents events;

    /**
     * IDEN-01 (SC#1): invite → INVITED 로 저장되고 email 이 일치한다.
     */
    @Test
    void inviteCreatesInvitedUser() {
        Long userId = identityService.invite("invite-" + System.nanoTime() + "@x.com");

        User found = userRepository.findById(userId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(UserStatus.INVITED);
        assertThat(found.getEmail().getValue()).startsWith("invite-");
    }

    /**
     * IDEN-05 UNIQUE (SC#1): 동일 email 두 번 초대 → 선검사 EmailAlreadyExists 로 거부된다.
     */
    @Test
    void duplicateEmailIsRejected() {
        String email = "dup-" + System.nanoTime() + "@x.com";
        identityService.invite(email);

        assertThatThrownBy(() -> identityService.invite(email))
                .isInstanceOf(EmailAlreadyExists.class);
    }

    /**
     * IDEN-05 UNIQUE 최종 방어선: 선검사(existsByEmail)를 우회해도 DB UNIQUE(email) 제약이
     * 중복을 거부한다. 동시 invite race window 에서 두 번째 저장이 DataIntegrityViolationException
     * 으로 차단됨을 직접 검증한다(선검사는 UX, DB 제약이 결정적 방어 — D-06).
     */
    @Test
    void dbUniqueConstraintRejectsDuplicateEmail() {
        String email = "race-" + System.nanoTime() + "@x.com";
        userRepository.save(User.invite(new Email(email)));

        assertThatThrownBy(() -> userRepository.save(User.invite(new Email(email))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * IDEN-04 (SC#3): invite → linkIdentity → disable 후, save() 가 완료된 시점에
     * UserDisabled 이벤트 1건이 해당 userId 로 발행되고 status 가 DISABLED 다.
     */
    @Test
    void disablePublishesUserDisabledEventAfterSave() {
        Long userId = identityService.invite("disable-" + System.nanoTime() + "@x.com");
        identityService.linkIdentity(userId, "oid-" + System.nanoTime(), "홍길동");

        identityService.disable(userId);

        List<UserDisabled> disabledEvents = events.stream(UserDisabled.class).toList();
        assertThat(disabledEvents)
                .extracting(UserDisabled::userId)
                .containsExactly(userId);

        User found = userRepository.findById(userId).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(UserStatus.DISABLED);
    }

    /**
     * IDEN-05 (SC#5): linkIdentity 이후에도 불변 로컬 PK(User.id)로 동일 사용자를 조회할 수 있다.
     * 업무 참조 키는 email/externalId 가 아니라 불변 로컬 PK 다(A-6).
     */
    @Test
    void localPkRemainsBusinessReferenceAfterLinkIdentity() {
        Long userId = identityService.invite("pk-" + System.nanoTime() + "@x.com");
        identityService.linkIdentity(userId, "oid-" + System.nanoTime(), "홍길동");

        User found = userRepository.findById(userId).orElseThrow();
        assertThat(found.getId()).isEqualTo(userId);
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }
}
