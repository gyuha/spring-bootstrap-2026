package com.example.bootstrap.batch.application.service;

import com.example.bootstrap.batch.application.dto.BatchJobResult;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import java.time.LocalDateTime;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 배치 잡 실행 서비스.
 *
 * <p>Spring Batch의 동기(blocking) {@link JobLauncher}를 WebFlux 리액티브 체인에
 * 안전하게 통합하기 위해 {@link Mono#fromCallable}과
 * {@link Schedulers#boundedElastic()} 스레드풀을 사용하여 이벤트 루프를 차단하지 않습니다.</p>
 */
@Service
public class BatchJobService {

    private final Job expiredTokenCleanupJob;
    private final JobLauncher jobLauncher;

    /**
     * BatchJobService 생성자.
     *
     * @param expiredTokenCleanupJob 만료 토큰 정리 배치 잡
     * @param jobLauncher            Spring Batch 잡 실행기
     */
    public BatchJobService(
            final Job expiredTokenCleanupJob,
            final JobLauncher jobLauncher) {
        this.expiredTokenCleanupJob = expiredTokenCleanupJob;
        this.jobLauncher = jobLauncher;
    }

    /**
     * 만료 Refresh Token 정리 배치 잡을 실행합니다.
     *
     * <p>각 실행마다 고유한 {@link JobParameters}({@code runAt} = 현재 시각)를 생성하여
     * Spring Batch가 동일 잡의 새 인스턴스로 인식하도록 합니다.
     * 잡 실행 중 오류 발생 시 {@link BusinessException}({@link ErrorCode#BATCH_002})으로 변환합니다.</p>
     *
     * @return 배치 잡 실행 결과 {@link Mono}
     */
    public Mono<BatchJobResult> runExpiredTokenCleanup() {
        return Mono.fromCallable(() -> {
            JobParameters params = new JobParametersBuilder()
                .addLocalDateTime("runAt", LocalDateTime.now())
                .toJobParameters();
            JobExecution execution = jobLauncher.run(expiredTokenCleanupJob, params);
            return toBatchJobResult(execution);
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(
            ex -> !(ex instanceof BusinessException),
            ex -> new BusinessException(ErrorCode.BATCH_002, ex.getMessage())
        );
    }

    /**
     * {@link JobExecution}을 {@link BatchJobResult} DTO로 변환합니다.
     *
     * <p>모든 Step 실행의 {@code writeCount}를 합산하여 전체 처리 건수를 계산합니다.</p>
     *
     * @param execution Spring Batch 잡 실행 결과
     * @return API 응답용 배치 잡 결과 DTO
     */
    private BatchJobResult toBatchJobResult(final JobExecution execution) {
        long totalWriteCount = execution.getStepExecutions().stream()
            .mapToLong(StepExecution::getWriteCount)
            .sum();

        return new BatchJobResult(
            execution.getJobInstance().getJobName(),
            execution.getStatus().name(),
            totalWriteCount,
            execution.getStartTime(),
            execution.getEndTime()
        );
    }
}
