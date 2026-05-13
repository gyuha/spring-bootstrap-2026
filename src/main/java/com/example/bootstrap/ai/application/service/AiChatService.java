package com.example.bootstrap.ai.application.service;

import com.example.bootstrap.ai.application.dto.ChatRequest;
import com.example.bootstrap.ai.application.dto.ChatResponse;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Spring AI OpenAI 채팅 서비스.
 *
 * <p>동기 블로킹 AI 호출은 이벤트 루프를 차단하지 않도록
 * {@link Schedulers#boundedElastic()} 스레드풀에서 격리 실행됩니다.
 * SSE 스트리밍은 Spring AI의 네이티브 {@link Flux} 응답을 직접 반환합니다.
 */
@Service
public class AiChatService {

    private final ChatClient chatClient;

    /**
     * AiChatService 생성자.
     *
     * @param chatClient Spring AI {@link ChatClient} 인스턴스 (AiConfig Bean)
     */
    public AiChatService(final ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 동기 AI 채팅 요청을 처리합니다.
     *
     * <p>Spring AI의 블로킹 {@code ChatClient.call().content()} 호출을
     * {@link Mono#fromCallable}과 {@link Schedulers#boundedElastic()}으로 래핑하여
     * WebFlux 이벤트 루프를 차단하지 않습니다.
     * 예외 발생 시 {@link BusinessException}({@link ErrorCode#AI_001})으로 변환합니다.
     *
     * @param request 채팅 요청 DTO
     * @return AI 응답 DTO를 포함한 {@link Mono}
     */
    public Mono<ChatResponse> chat(final ChatRequest request) {
        return Mono.fromCallable(() -> {
            String content = chatClient.prompt()
                    .user(request.message())
                    .call()
                    .content();
            return new ChatResponse(content != null ? content : "");
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(
                ex -> !(ex instanceof BusinessException),
                ex -> new BusinessException(ErrorCode.AI_001, ex.getMessage()));
    }

    /**
     * SSE 스트리밍 AI 채팅 요청을 처리합니다.
     *
     * <p>Spring AI의 {@code ChatClient.stream().content()}는 네이티브 {@link Flux}를 반환하므로
     * 별도의 스레드 격리 없이 리액티브 체인에서 직접 사용합니다.
     * 예외 발생 시 {@link BusinessException}({@link ErrorCode#AI_001})으로 변환합니다.
     *
     * @param request 채팅 요청 DTO
     * @return AI 응답 텍스트 토큰 스트림 {@link Flux}
     */
    public Flux<String> stream(final ChatRequest request) {
        return chatClient.prompt()
                .user(request.message())
                .stream()
                .content()
                .onErrorMap(
                        ex -> !(ex instanceof BusinessException),
                        ex -> new BusinessException(ErrorCode.AI_001, ex.getMessage()));
    }
}
