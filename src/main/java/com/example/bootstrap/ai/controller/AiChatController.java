package com.example.bootstrap.ai.controller;

import com.example.bootstrap.ai.application.dto.ChatRequest;
import com.example.bootstrap.ai.application.dto.ChatResponse;
import com.example.bootstrap.ai.application.service.AiChatService;
import com.example.bootstrap.global.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI 채팅 REST 컨트롤러.
 *
 * <p>동기 채팅({@code POST /api/v1/ai/chat})과
 * SSE 스트리밍 채팅({@code GET /api/v1/ai/chat/stream}) 엔드포인트를 제공합니다.
 * 모든 엔드포인트는 JWT 인증이 필요합니다.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * AiChatController 생성자.
     *
     * @param aiChatService AI 채팅 서비스
     */
    public AiChatController(final AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    /**
     * 동기 AI 채팅 응답을 반환합니다.
     *
     * <p>요청 본문의 {@code message}를 OpenAI에 전달하고
     * 완성된 응답을 Envelope 형식으로 반환합니다.
     *
     * @param request 채팅 요청 DTO ({@code message} 필수, {@code model} 옵셔널)
     * @return 200 OK와 AI 응답 Envelope {@link ApiResponse}
     */
    @PostMapping("/chat")
    public Mono<ResponseEntity<ApiResponse<ChatResponse>>> chat(
            @Valid @RequestBody final ChatRequest request) {
        return aiChatService.chat(request)
                .map(response -> ResponseEntity.ok(
                        ApiResponse.success("AI 응답이 생성되었습니다.", response)));
    }

    /**
     * SSE 스트리밍 AI 채팅 응답을 반환합니다.
     *
     * <p>OpenAI 응답 토큰을 {@code text/event-stream} 형식으로 실시간 스트리밍합니다.
     * 클라이언트는 SSE 프로토콜로 토큰을 순차적으로 수신합니다.
     *
     * @param message 사용자 채팅 메시지 (필수)
     * @param model   OpenAI 모델명 (옵셔널, 미입력 시 gpt-4o-mini)
     * @return AI 응답 텍스트 토큰 스트림
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam final String message,
            @RequestParam(required = false) final String model) {
        return aiChatService.stream(new ChatRequest(message, model));
    }
}
