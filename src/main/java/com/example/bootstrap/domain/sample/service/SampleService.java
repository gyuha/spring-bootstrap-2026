package com.example.bootstrap.domain.sample.service;

import com.example.bootstrap.domain.sample.dto.SampleCreateRequest;
import com.example.bootstrap.domain.sample.dto.SampleResponse;
import com.example.bootstrap.domain.sample.entity.Sample;
import com.example.bootstrap.domain.sample.mapper.SampleQueryMapper;
import com.example.bootstrap.domain.sample.repository.SampleRepository;
import com.example.bootstrap.global.exception.BusinessException;
import com.example.bootstrap.global.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sample 비즈니스 로직 + 트랜잭션 경계 (FOUND-01/02, D-08).
 *
 * <p>쓰기/단건 CRUD는 JPA {@link SampleRepository}, 조회는 MyBatis {@link SampleQueryMapper}로
 * 처리한다. 둘은 동일 DataSource·단일 JpaTransactionManager를 공유하므로 같은 물리 트랜잭션에서
 * 동작한다.
 */
@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleRepository sampleRepository;
    private final SampleQueryMapper sampleQueryMapper;

    @Transactional(readOnly = true)
    public List<SampleResponse> findAll() {
        return sampleQueryMapper.findAll();
    }

    @Transactional
    public SampleResponse create(SampleCreateRequest request) {
        Sample saved = sampleRepository.save(Sample.create(request.title()));
        return new SampleResponse(saved.getId(), saved.getTitle(), saved.getCreatedAt());
    }

    @Transactional
    public void delete(UUID id) {
        Sample sample = sampleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SAMPLE_NOT_FOUND));
        sample.softDelete();
    }
}
