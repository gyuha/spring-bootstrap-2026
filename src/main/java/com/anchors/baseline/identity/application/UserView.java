package com.anchors.baseline.identity.application;

/**
 * Identity 컨텍스트 신원 읽기 DTO (CQRS-lite, AUTH-08 / D-01). User 애그리거트와 Email/UserStatus VO 를
 * interfaces 계층에 노출하지 않도록 application 에서 평탄화한 읽기 전용 표현이다.
 *
 * <p>영속 기술 애너테이션(@Entity/@Param 등)을 두지 않는다 — 순수 read DTO.
 */
public record UserView(Long id, String email, String status) {
}
