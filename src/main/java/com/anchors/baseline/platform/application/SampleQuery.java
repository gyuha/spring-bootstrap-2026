package com.anchors.baseline.platform.application;

import java.util.List;

/**
 * 복잡 조회(읽기 모델) 포트. 애플리케이션이 정의하고 infrastructure 의 MyBatis 어댑터가 구현한다
 * (헥사고날 — application 은 MyBatis 를 모른다).
 */
public interface SampleQuery {

    List<SampleDto> findAll();
}
