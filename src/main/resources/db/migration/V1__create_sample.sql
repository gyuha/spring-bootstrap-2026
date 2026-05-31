-- Flyway baseline 마이그레이션 (FOUND-05). 적용 후 immutable — 변경은 새 V 파일로 (D-14).
-- samples: domain.sample 참조 슬라이스 테이블. BaseEntity 공통 컬럼 규약 반영.
CREATE TABLE samples (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    title      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
