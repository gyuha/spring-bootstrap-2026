package com.example.bootstrap.global.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 설정 클래스.
 *
 * <p>Spring Boot 자동 구성이 제공하는 {@link ChatClient.Builder}를 사용하여
 * {@link ChatClient} 빈을 생성합니다.
 * 서비스 계층은 {@link ChatClient}를 직접 주입받아 단위 테스트 시 Mock으로 교체할 수 있습니다.
 */
@Configuration
public class AiConfig {

    /**
     * Spring AI ChatClient 빈을 생성합니다.
     *
     * @param builder Spring Boot 자동 구성의 {@link ChatClient.Builder}
     * @return 초기화된 {@link ChatClient} 인스턴스
     */
    @Bean
    public ChatClient chatClient(final ChatClient.Builder builder) {
        return builder.build();
    }
}
