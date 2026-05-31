package com.anchors.baseline.authorization.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 읽기 모델 DTO (CQRS-lite) — evaluate 합산의 raw grant 한 행. 평탄 프로젝션(Pitfall 5).
 * record 가 아니라 클래스다 — MyBatis 어댑터(Wave 2)가 무인자 생성자로 매핑한다(SampleDto 형판).
 * source 는 부여 경로(direct/group/inherited 등)를 식별하기 위한 라벨이다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveGrantDto {

    private String role;
    private Long resourceId;
    private String source;
}
