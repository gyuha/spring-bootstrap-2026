package com.example.bootstrap.batch.controller;

import com.example.bootstrap.batch.application.dto.BatchJobResult;
import com.example.bootstrap.batch.application.service.BatchJobService;
import com.example.bootstrap.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * 배치 관리 REST 컨트롤러.
 *
 * <p>ADMIN 역할 전용 배치 잡 트리거 엔드포인트를 제공합니다.
 * {@code /api/v1/admin/**} 경로는 {@code SecurityConfig}에서 {@code ADMIN} 역할을 요구합니다.</p>
 *
 * <ul>
 *   <li>{@code POST /api/v1/admin/batch/expired-tokens} — 만료 Refresh Token 정리</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/batch")
public class BatchController {

    private final BatchJobService batchJobService;

    /**
     * BatchController 생성자.
     *
     * @param batchJobService 배치 잡 실행 서비스
     */
    public BatchController(final BatchJobService batchJobService) {
        this.batchJobService = batchJobService;
    }

    /**
     * 만료 Refresh Token 정리 배치 잡을 실행합니다.
     *
     * <p>Refresh Token Rotation 방식으로 누적된 만료 토큰을 일괄 삭제합니다.
     * 잡은 {@link reactor.core.scheduler.Schedulers#boundedElastic()} 스레드풀에서
     * 격리 실행되므로 WebFlux 이벤트 루프를 차단하지 않습니다.</p>
     *
     * @return 200 OK와 배치 잡 실행 결과 {@link ApiResponse}
     */
    @PostMapping("/expired-tokens")
    public Mono<ResponseEntity<ApiResponse<BatchJobResult>>> runExpiredTokenCleanup() {
        return batchJobService.runExpiredTokenCleanup()
            .map(result -> ResponseEntity.ok(
                ApiResponse.success("만료 토큰 정리 배치 작업이 완료되었습니다.", result)
            ));
    }
}
