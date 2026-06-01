package com.example.bootstrap.domain.sample;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bootstrap.global.BaseIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * domain.sample 슬라이스가 배선 없이 동작하는지 검증 (FOUND-01, D-06).
 *
 * <p>POST → 201, GET → 200(생성 항목 포함), DELETE 후 GET → soft-delete로 제외됨을 확인한다.
 * 별도 배선 없이 패키지/레이어 규칙만으로 엔드포인트가 동작함을 증명한다(D-05).
 */
@AutoConfigureMockMvc
class SampleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void createGetDeleteFlowWorksWithoutExtraWiring() throws Exception {
        String body = """
                {"title":"integration-sample"}
                """;

        // POST → 201, ApiResponse.data에 생성 결과 (02-03 secure-by-default — 인증 필요)
        String response = mockMvc.perform(post("/samples")
                        .header("Authorization", userBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.title").value("integration-sample"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String id = JsonPath.read(response, "$.data.id");

        // GET → 200, 생성 항목 포함 (MyBatis findAll)
        mockMvc.perform(get("/samples").header("Authorization", userBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(id)));

        // DELETE → 204
        mockMvc.perform(delete("/samples/{id}", id).header("Authorization", userBearer()))
                .andExpect(status().isNoContent());

        // GET → soft-delete된 항목 제외 (deleted_at IS NULL, D-10)
        mockMvc.perform(get("/samples").header("Authorization", userBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", not(hasItem(id))));
    }
}
