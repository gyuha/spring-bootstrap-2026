CREATE TABLE IF NOT EXISTS platform_sample (
    id BIGSERIAL PRIMARY KEY,
    value VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

COMMENT ON TABLE platform_sample IS 'Phase 1 JPA+MyBatis 통합 검증용 샘플 테이블 — 업무 도메인과 무관';
