-- V2__create_users.sql — V1 immutable, 신규 파일로만 추가 (D-41).
-- users: domain.user 슬라이스 테이블. BaseEntity 공통 컬럼 규약 반영(V1과 동일).
CREATE TABLE users (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,            -- BCrypt 해시 (~60자, D-32)
    role       VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ  NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 부분 유니크: 활성 사용자(deleted_at IS NULL)만 email 유니크 → 삭제 사용자 email 재가입 가능
-- (D-34, Phase 3 ADMIN-04 선반영). 엔티티에 unique=true를 두지 않고 제약은 DB가 소유.
CREATE UNIQUE INDEX ux_users_email_active ON users (email) WHERE deleted_at IS NULL;
