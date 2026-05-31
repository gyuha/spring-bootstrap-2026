package com.anchors.baseline.identity.domain.event;

/**
 * 사용자 비활성화 도메인 이벤트(IDEN-04).
 * 컨텍스트 간 참조는 ID로만(§4.1) — 페이로드는 로컬 PK userId 뿐, PII 미포함.
 */
public record UserDisabled(Long userId) {
}
