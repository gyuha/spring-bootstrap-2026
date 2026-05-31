package com.example.bootstrap.domain.sample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bootstrap.global.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * domain.sample 슬라이스가 배선 없이 동작하는지 검증 (FOUND-01).
 *
 * <p>Wave 0 스캐폴드 — placeholder GREEN. SampleController/Service/Repository는 01-03에서
 * 생성되고, GET /samples → 200 실제 assertion도 그때 채운다.
 */
@AutoConfigureMockMvc
class SampleIntegrationTest extends BaseIntegrationTest {

    @Test
    void placeholder_getSamplesReturns200() {
        // TODO(01-03): GET /samples 호출 시 200 응답 검증 (스캐폴드 슬라이스 동작 증명)
        assertTrue(true);
    }
}
