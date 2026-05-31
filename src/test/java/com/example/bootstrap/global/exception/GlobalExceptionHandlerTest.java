package com.example.bootstrap.global.exception;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bootstrap.global.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * BusinessException → ProblemDetail 응답 검증 (FOUND-04).
 *
 * <p>Wave 0 스캐폴드 — placeholder GREEN. 실제 assertion(application/problem+json,
 * errorCode 프로퍼티)은 01-03에서 채운다.
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest extends BaseIntegrationTest {

    @Test
    void placeholder_businessExceptionReturnsProblemDetail() {
        // TODO(01-03): BusinessException 발생 시 Content-Type=application/problem+json,
        //              errorCode 프로퍼티 포함 검증
        assertTrue(true);
    }
}
