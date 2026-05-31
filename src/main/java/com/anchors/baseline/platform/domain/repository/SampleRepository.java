package com.anchors.baseline.platform.domain.repository;

import com.anchors.baseline.platform.domain.model.SampleEntity;

/**
 * SampleEntity 영속화 포트 — 도메인이 정의하고 infrastructure 가 구현한다(헥사고날 포트).
 */
public interface SampleRepository {

    SampleEntity save(SampleEntity entity);
}
