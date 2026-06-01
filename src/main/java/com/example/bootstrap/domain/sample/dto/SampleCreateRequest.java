package com.example.bootstrap.domain.sample.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Sample 생성 요청 DTO. {@code @Valid}로 컨트롤러에서 검증된다 (D-18).
 */
public record SampleCreateRequest(@NotBlank String title) {
}
