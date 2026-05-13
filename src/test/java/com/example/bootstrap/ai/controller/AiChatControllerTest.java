package com.example.bootstrap.ai.controller;

import com.example.bootstrap.ai.application.dto.ChatRequest;
import com.example.bootstrap.ai.application.dto.ChatResponse;
import com.example.bootstrap.ai.application.service.AiChatService;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AiChatController} 단위 테스트.
 *
 * <p>{@link AiChatService}는 Mockito로 대체됩니다.
 * 컨트롤러의 응답 변환, 상태 코드, Envelope 포장 로직을 검증합니다.
 */
@ExtendWith(MockitoExtension.class)
class AiChatControllerTest {

    private static final String TEST_MESSAGE = "Hello, AI!";
    private static final String TEST_CONTENT = "I am an AI assistant!";

    @Mock
    private AiChatService aiChatService;

    private AiChatController aiChatController;

    @BeforeEach
    void setUp() {
        aiChatController = new AiChatController(aiChatService);
    }

    // ── chat ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("chat: 정상 요청 시 HTTP 200과 SUCCESS 코드를 포함한 응답을 반환한다")
    void chat_whenValidRequest_returns200WithSuccessCode() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        when(aiChatService.chat(any(ChatRequest.class)))
                .thenReturn(Mono.just(new ChatResponse(TEST_CONTENT)));

        StepVerifier.create(aiChatController.chat(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
                    assertThat(responseEntity.getBody()).isNotNull();
                    assertThat(responseEntity.getBody().code()).isEqualTo("SUCCESS");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("chat: AI 응답 content가 Envelope data 필드에 올바르게 포함된다")
    void chat_whenSuccessful_wrapsResponseInEnvelopeDataField() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        when(aiChatService.chat(any(ChatRequest.class)))
                .thenReturn(Mono.just(new ChatResponse(TEST_CONTENT)));

        StepVerifier.create(aiChatController.chat(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getBody()).isNotNull();
                    assertThat(responseEntity.getBody().data()).isNotNull();
                    assertThat(responseEntity.getBody().data().content()).isEqualTo(TEST_CONTENT);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("chat: 서비스가 BusinessException을 반환하면 오류가 컨트롤러를 통해 전파된다")
    void chat_whenServiceThrowsBusinessException_propagatesError() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        when(aiChatService.chat(any(ChatRequest.class)))
                .thenReturn(Mono.error(new BusinessException(ErrorCode.AI_001)));

        StepVerifier.create(aiChatController.chat(request))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_001);
                })
                .verify();
    }

    @Test
    @DisplayName("chat: 응답 errors 필드는 성공 시 null이다")
    void chat_whenSuccessful_responseErrorsIsNull() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        when(aiChatService.chat(any(ChatRequest.class)))
                .thenReturn(Mono.just(new ChatResponse(TEST_CONTENT)));

        StepVerifier.create(aiChatController.chat(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getBody()).isNotNull();
                    assertThat(responseEntity.getBody().errors()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("chat: 빈 content 응답도 Envelope에 정상적으로 포장된다")
    void chat_whenEmptyContent_wrapsEmptyResponseInEnvelope() {
        ChatRequest request = new ChatRequest(TEST_MESSAGE, null);
        when(aiChatService.chat(any(ChatRequest.class)))
                .thenReturn(Mono.just(new ChatResponse("")));

        StepVerifier.create(aiChatController.chat(request))
                .assertNext(responseEntity -> {
                    assertThat(responseEntity.getBody()).isNotNull();
                    assertThat(responseEntity.getBody().data()).isNotNull();
                    assertThat(responseEntity.getBody().data().content()).isEmpty();
                })
                .verifyComplete();
    }

    // ── stream ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("stream: 정상 요청 시 AI 응답 토큰 스트림을 반환한다")
    void stream_whenValidRequest_returnsTokenFlux() {
        when(aiChatService.stream(any(ChatRequest.class)))
                .thenReturn(Flux.just("Hello", " World"));

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, null))
                .expectNext("Hello", " World")
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: 모델 파라미터를 제공하면 서비스에 정상적으로 전달된다")
    void stream_whenModelProvided_callsServiceWithCorrectRequest() {
        when(aiChatService.stream(any(ChatRequest.class)))
                .thenReturn(Flux.just("response"));

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, "gpt-4o"))
                .expectNext("response")
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: 서비스 오류 발생 시 에러가 Flux를 통해 전파된다")
    void stream_whenServiceThrowsError_propagatesErrorThroughFlux() {
        when(aiChatService.stream(any(ChatRequest.class)))
                .thenReturn(Flux.error(new BusinessException(ErrorCode.AI_001)));

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, null))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_001);
                })
                .verify();
    }

    @Test
    @DisplayName("stream: model이 null인 경우에도 스트리밍이 정상 동작한다")
    void stream_whenModelIsNull_streamsSuccessfully() {
        when(aiChatService.stream(any(ChatRequest.class)))
                .thenReturn(Flux.just("token1", "token2"));

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, null))
                .expectNext("token1", "token2")
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: message와 model이 서비스에 정확한 ChatRequest로 전달된다")
    void stream_whenCalled_passesCorrectChatRequestToService() {
        String model = "gpt-4o";
        when(aiChatService.stream(argThat(req ->
                TEST_MESSAGE.equals(req.message()) && model.equals(req.model()))))
                .thenReturn(Flux.just("verified"));

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, model))
                .expectNext("verified")
                .verifyComplete();

        verify(aiChatService).stream(argThat(req ->
                TEST_MESSAGE.equals(req.message()) && model.equals(req.model())));
    }

    @Test
    @DisplayName("stream: 일부 토큰 방출 후 에러 발생 시 에러가 Flux를 통해 전파된다")
    void stream_whenPartialTokensThenError_propagatesErrorAfterTokens() {
        Flux<String> partialThenError = Flux.concat(
                Flux.just("first", " chunk"),
                Flux.error(new BusinessException(ErrorCode.AI_001, "Stream failed"))
        );
        when(aiChatService.stream(any(ChatRequest.class))).thenReturn(partialThenError);

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, null))
                .expectNext("first", " chunk")
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getErrorCode())
                            .isEqualTo(ErrorCode.AI_001);
                })
                .verify();
    }

    @Test
    @DisplayName("stream: 빈 Flux를 반환해도 에러 없이 정상 완료된다")
    void stream_whenEmptyFlux_completesWithoutError() {
        when(aiChatService.stream(any(ChatRequest.class))).thenReturn(Flux.empty());

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, null))
                .verifyComplete();
    }

    @Test
    @DisplayName("stream: model null인 ChatRequest가 서비스에 정확하게 전달된다")
    void stream_whenModelIsNull_passesNullModelInChatRequest() {
        when(aiChatService.stream(argThat(req ->
                TEST_MESSAGE.equals(req.message()) && req.model() == null)))
                .thenReturn(Flux.just("ok"));

        StepVerifier.create(aiChatController.stream(TEST_MESSAGE, null))
                .expectNext("ok")
                .verifyComplete();

        verify(aiChatService).stream(argThat(req ->
                TEST_MESSAGE.equals(req.message()) && req.model() == null));
    }
}
