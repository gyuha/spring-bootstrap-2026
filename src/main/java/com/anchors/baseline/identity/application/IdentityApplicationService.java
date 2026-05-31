package com.anchors.baseline.identity.application;

import com.anchors.baseline.identity.domain.model.Email;
import com.anchors.baseline.identity.domain.model.User;
import com.anchors.baseline.identity.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity 유스케이스 조율 + 트랜잭션 경계(정본 §4.3). 비즈니스 규칙은 없다 —
 * 전이·멱등성 가드는 모두 User 애그리거트에 있고, 여기서는 포트(UserRepository)만 조율한다.
 * 도메인 이벤트는 save() 시점에 자동 발행되므로 ApplicationEventPublisher 를 주입하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class IdentityApplicationService {

    private final UserRepository userRepository;

    /**
     * IDEN-01: email 로 미로그인 사용자를 초대한다. email 선검사는 UX 용이고,
     * 동시성 최종 방어는 DB UNIQUE 제약(IDEN-05/D-06)이다.
     */
    public Long invite(String emailValue) {
        Email email = new Email(emailValue);
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExists("이미 초대된 이메일입니다: " + emailValue);
        }
        User saved = userRepository.save(User.invite(email));
        return saved.getId();
    }

    /**
     * IDEN-02/03/06: 최초 로그인 시 IdP 신원을 연결한다. 전이·멱등성 가드는 User 내부.
     */
    public void linkIdentity(Long userId, String externalId, String idpDisplayName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        user.linkIdentity(externalId, idpDisplayName);
        userRepository.save(user);
    }

    /**
     * IDEN-04: 사용자를 비활성화한다. save() 가 UserDisabled 이벤트를 발행한다.
     */
    public void disable(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId));
        user.disable();
        userRepository.save(user);
    }
}
