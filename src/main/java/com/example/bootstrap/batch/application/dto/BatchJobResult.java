package com.example.bootstrap.batch.application.dto;

import java.time.LocalDateTime;

/**
 * 배치 잡 실행 결과 DTO.
 *
 * <p>Spring Batch {@link org.springframework.batch.core.JobExecution} 결과를
 * API 응답용으로 변환한 불변 레코드입니다.</p>
 *
 * @param jobName    실행된 잡 이름
 * @param status     잡 실행 상태 (COMPLETED, FAILED 등)
 * @param writeCount 처리된 항목(삭제된 토큰) 수
 * @param startTime  실행 시작 시간
 * @param endTime    실행 종료 시간
 */
public record BatchJobResult(
    String jobName,
    String status,
    long writeCount,
    LocalDateTime startTime,
    LocalDateTime endTime
) {
}
