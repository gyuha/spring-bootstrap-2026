package com.example.bootstrap.ai.application.service;

import com.example.bootstrap.ai.application.dto.ChatRequest;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiChatService} 단위 테스트.
 *
 * <p>{@link ChatClient}와 중첩 스펙 인터페이스는 Mockito로 대체됩니다.
 * 리액티브 체인은 Project Reactor의 {@link StepVerifier}로 검증됩니다.
 * {@code subscribeOn(Schedulers.boundedElastic())} 격리도 StepVerifier가 투명하게 처리합니다.
 */
@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    private static final String TEST_MESSAGE = "Hello, AI!";
    private static final String TEST_RESPONSE = "Hello, I am an AI assistant!";

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClient.ChatClientRequestSpec promptSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private AiChatService aiChatService;

    @BeforeEach
    void setUp() {
        aiChatService = new AiChatService(chatClient);
    }

    // ── chat ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("chat: 정상 요청 시 AI 응답 내용을 포함한 ChatResponse를 반환한다")
    void chat_whenValidRequest_returnsChatResponseWithContent() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubSyncCall(TEST_RESPONSE);

        StepVerifier.create(aiChatService.chat(request))
                .assertNext(response -> {
                    assertThat(response).isNotNull();
                    assertThat(response.content()).isEqualTo(TEST_RESPONSE);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("chat: ChatClient.content()가 null인 경우 빈 문자열 content를 반환한다")
    void chat_whenContentIsNull_returnsEmptyStringContent() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubSyncCall(null);

        StepVerifier.create(aiChatService.chat(request))
                .assertNext(response -> assertThat(response.content()).isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("chat: ChatClient 내부 예외 발생 시 BusinessException(AI_001)으로 변환된다")
    void chat_whenChatClientThrowsRuntimeException_mapsToAi001BusinessException() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenThrow(new RuntimeException("OpenAI API connection error"));

        StepVerifier.create(aiChatService.chat(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_001);
                })
                .verify();
    }

    @Test
    @DisplayName("chat: 이미 BusinessException인 경우 AI_001로 재포장하지 않고 원래 예외를 전파한다")
    void chat_whenAlreadyBusinessException_propagatesOriginalErrorCode() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        BusinessException originalEx = new BusinessException(ErrorCode.AI_002, "Bad request");
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenThrow(originalEx);

        StepVerifier.create(aiChatService.chat(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_002);
                })
                .verify();
    }

    @Test
    @DisplayName("chat: 모델 파라미터가 제공된 경우에도 정상적으로 AI 응답을 반환한다")
    void chat_whenModelOverrideProvided_returnsResponseSuccessfully() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, "gpt-4o");
        stubSyncCall(TEST_RESPONSE);

        StepVerifier.create(aiChatService.chat(request))
                .assertNext(response -> assertThat(response.content()).isEqualTo(TEST_RESPONSE))
                .verifyComplete();
    }

    @Test
    @DisplayName("chat: 긴 메시지에도 정상적으로 AI 응답을 반환한다")
    void chat_whenLongMessage_returnsResponseSuccessfully() {
        String longMessage = "A".repeat(3000);
        ChatRequest request = new ChatRequest(longMessage, null);
        stubSyncCall("Long response");

        StepVerifier.create(aiChatService.chat(request))
                .assertNext(response -> assertThat(response.content()).isEqualTo("Long response"))
                .verifyComplete();
    }

    @Test
    @DisplayName("chat: 반환된 Mono는 정확히 하나의 ChatResponse만 방출하고 완료된다")
    void chat_whenSuccessful_emitsExactlyOneChatResponseAndCompletes() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubSyncCall(TEST_RESPONSE);

        StepVerifier.create(aiChatService.chat(request))
                .expectNextCount(1)
                .verifyComplete();
    }

    // ── stream ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stream: 정상 요청 시 AI 응답 토큰 Flux를 반환한다")
    void stream_whenValidRequest_returnsTokenFlux() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubStreamCall(Flux.just("Hello", ", ", "World", "!"));

        StepVerifier.create(aiChatService.stream(request))
                .expectNext("Hello", ", ", "World", "!")
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: 빈 스트림을 반환해도 에러 없이 완료된다")
    void stream_whenEmptyStream_completesWithoutError() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubStreamCall(Flux.empty());

        StepVerifier.create(aiChatService.stream(request))
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: 스트리밍 중 런타임 예외 발생 시 BusinessException(AI_001)으로 변환된다")
    void stream_whenStreamThrowsRuntimeException_mapsToAi001BusinessException() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubStreamCall(Flux.error(new RuntimeException("SSE stream interrupted")));

        StepVerifier.create(aiChatService.stream(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_001);
                })
                .verify();
    }

    @Test
    @DisplayName("stream: 단일 토큰 응답도 정상적으로 처리된다")
    void stream_whenSingleToken_emitsTokenAndCompletes() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubStreamCall(Flux.just("SingleToken"));

        StepVerifier.create(aiChatService.stream(request))
                .expectNext("SingleToken")
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: 다수의 토큰을 순서대로 방출한다")
    void stream_whenMultipleTokens_emitsInOrder() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubStreamCall(Flux.just("Token1", "Token2", "Token3"));

        StepVerifier.create(aiChatService.stream(request))
                .expectNext("Token1")
                .expectNext("Token2")
                .expectNext("Token3")
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: 일부 토큰 방출 후 런타임 예외 발생 시 BusinessException(AI_001)으로 변환된다")
    void stream_whenPartialTokensThenRuntimeError_mapsToAi001BusinessException() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        Flux<String> partialThenError = Flux.concat(
                Flux.just("partial", " response"),
                Flux.error(new RuntimeException("Connection dropped mid-stream"))
        );
        stubStreamCall(partialThenError);

        StepVerifier.create(aiChatService.stream(request))
                .expectNext("partial", " response")
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_001);
                })
                .verify();
    }

    @Test
    @DisplayName("stream: 스트리밍 중 이미 BusinessException인 경우 AI_001로 재포장하지 않고 원래 예외를 전파한다")
    void stream_whenAlreadyBusinessException_propagatesOriginalErrorCode() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        BusinessException originalEx = new BusinessException(ErrorCode.AI_002, "Model not found");
        stubStreamCall(Flux.error(originalEx));

        StepVerifier.create(aiChatService.stream(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_002);
                })
                .verify();
    }

    @Test
    @DisplayName("stream: 모델 파라미터가 제공된 경우에도 스트리밍이 정상 동작한다")
    void stream_whenModelOverrideProvided_streamsSuccessfully() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, "gpt-4o");
        stubStreamCall(Flux.just("Model", " override", " works"));

        StepVerifier.create(aiChatService.stream(request))
                .expectNext("Model", " override", " works")
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: 스트리밍 완료 시 정확히 모든 토큰이 방출된 후 종료된다")
    void stream_whenCompleted_emitsAllTokensBeforeTermination() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        String[] tokens = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "token" + i)
                .toArray(String[]::new);
        stubStreamCall(Flux.fromArray(tokens));

        StepVerifier.create(aiChatService.stream(request))
                .expectNext(tokens)
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: chatClient.prompt()가 호출되고 user 메시지가 올바르게 전달된다")
    void stream_whenCalled_invokesPromptWithCorrectUserMessage() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        stubStreamCall(Flux.just("response"));

        StepVerifier.create(aiChatService.stream(request))
                .expectNext("response")
                .verifyComplete();

        verify(chatClient).prompt();
        verify(promptSpec).user(TEST_MESSAGE);
        verify(promptSpec).stream();
        verify(streamResponseSpec).content();
    }

    @Test
    @DisplayName("stream: AI_003(타임아웃) BusinessException인 경우도 재포장 없이 전파된다")
    void stream_whenTimeoutBusinessException_propagatesWithoutRewrapping() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        BusinessException timeoutEx = new BusinessException(ErrorCode.AI_003, "AI response timeout");
        stubStreamCall(Flux.error(timeoutEx));

        StepVerifier.create(aiChatService.stream(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_003);
                })
                .verify();
    }

    // ── 헬퍼 메서드 ───────────────────────────────────────────────────────────

    private void stubSyncCall(final String responseContent) {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(responseContent);
    }

    private void stubStreamCall(final Flux<String> responseFlux) {
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(promptSpec);
        when(promptSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(responseFlux);
    }
}
