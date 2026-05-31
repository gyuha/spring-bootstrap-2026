CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    external_id   VARCHAR(255) UNIQUE,
    status        VARCHAR(20)  NOT NULL,
    display_name  VARCHAR(255),
    internal_note VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now() NOT NULL
);

COMMENT ON TABLE users IS 'Identity 컨텍스트 — 사용자 식별 진실 공급원 (Phase 2)';
