package com.anchors.baseline.common.infrastructure;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI(Swagger) 메타데이터 설정. cross-cutting 문서화 설정이므로 common/infrastructure에 둔다.
 *
 * <p>인증 SecurityScheme 배선은 의도적으로 포함하지 않는다 — Spring Security는 아직 도입되지
 * 않았으며(Phase 3/4 예정), 도입 후 인가 연동 시 여기에 SecurityScheme을 추가한다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI baselineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Baseline API")
                        .version("0.0.1-SNAPSHOT")
                        .description("Spring DDD 백엔드 베이스라인 API 문서"));
    }
}
