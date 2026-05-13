package com.example.bootstrap.ai.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * AI 채팅 요청 DTO.
 *
 * @param message 사용자 채팅 메시지 (필수, 최대 4000자)
 * @param model   OpenAI 모델명 (옵셔널, 미입력 시 application.yml 기본값 gpt-4o-mini 적용)
 */
public record ChatRequest(
    @NotBlank
    @Size(max = 4000)
    String message,
    String model) {
}
