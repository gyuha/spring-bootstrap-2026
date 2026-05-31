package com.anchors.baseline.platform.application;

import com.anchors.baseline.platform.domain.model.SampleEntity;
import com.anchors.baseline.platform.domain.repository.SampleRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * platform 샘플 유스케이스 조율. 쓰기는 도메인 포트(SampleRepository)를,
 * 복잡 조회는 읽기 포트(SampleQuery)를 통해 단일 트랜잭션 경계 안에서 처리한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SampleApplicationService {

    private final SampleRepository sampleRepository;
    private final SampleQuery sampleQuery;

    public Long save(String value) {
        SampleEntity saved = sampleRepository.save(new SampleEntity(value));
        return saved.getId();
    }

    public List<SampleDto> findAll() {
        return sampleQuery.findAll();
    }
}
