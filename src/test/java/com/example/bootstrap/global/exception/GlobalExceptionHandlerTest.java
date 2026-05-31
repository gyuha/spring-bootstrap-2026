package com.example.bootstrap.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bootstrap.global.BaseIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * BusinessException → RFC 9457 ProblemDetail 응답 검증 (FOUND-04, D-18).
 *
 * <p>존재하지 않는 id로 DELETE /samples/{id}를 호출하면 SampleService가
 * {@code BusinessException(SAMPLE_NOT_FOUND)}을 던지고, GlobalExceptionHandler가 이를
 * {@code application/problem+json} 404 응답으로 변환한다.
 */
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void businessExceptionReturnsProblemDetailWithErrorCode() throws Exception {
        mockMvc.perform(delete("/samples/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("SAMPLE_NOT_FOUND"));
    }
}
