package com.anchors.baseline.platform.application;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 읽기 모델 DTO (CQRS-lite). 애플리케이션·인터페이스 계층이 교환하는 쿼리 결과 타입이다.
 * record 가 아니라 클래스다 — MyBatis 어댑터가 무인자 생성자로 매핑한다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SampleDto {

    private Long id;
    private String value;
    private Instant createdAt;
}
