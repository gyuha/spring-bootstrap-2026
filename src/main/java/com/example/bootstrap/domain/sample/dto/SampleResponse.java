package com.example.bootstrap.domain.sample.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Sample 응답 DTO. MyBatis 조회 결과 매핑 및 서비스 반환 타입으로 공용 사용한다.
 *
 * @param createdAt 생성 시각 (UTC Instant, D-13)
 */
public record SampleResponse(UUID id, String title, Instant createdAt) {
}
