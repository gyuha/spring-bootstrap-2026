package com.anchors.baseline.auth.interfaces;

import java.util.Collections;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SC#2 검증용 보호 엔드포인트. principal 의 표준 attribute {@code user_id}(로컬 User.id, A-6)를 반환한다.
 *
 * <p>infrastructure 타입({@code BaselineOidcUser})을 import/cast 하지 않고 표준
 * {@code OidcUser.getAttribute("user_id")} 만 읽어 ArchUnit interfaces→infrastructure 위반을 구조적으로 제거한다.
 */
@RestController
public class MeController {

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser principal) {
        // singletonMap 은 null 값을 허용한다(Map.of 는 NPE). 정상 흐름에선 BaselineOidcUser 가 user_id 를
        // 항상 병합하지만, 표준 OidcUser 가 principal 일 경우(미배선) {"userId":null} 로 안전하게 응답한다.
        return Collections.singletonMap("userId", principal.getAttribute("user_id"));
    }
}
