-- V3__create_board.sql — Phase 4 생성형 게시판 (D-77). V1/V2 immutable, 신규 파일로만 추가.
-- boards/posts/comments는 BaseEntity 공통 컬럼 규약(V1/V2 형식) 복제.
-- reactions는 의도적 단순화: 하드 DELETE·upsert 멱등 모델(04-04 MyBatis 전용)이라
-- version/updated_at/deleted_at 불필요. JPA @Entity 미존재 → ddl-auto=validate 대상 외.

-- boards: domain.board 슬라이스. ADMIN 전용 생성(BOARD-01).
CREATE TABLE boards (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(100) NOT NULL,
    created_by UUID         NULL,                     -- 생성 ADMIN user UUID(04-02 서비스에서 세팅)
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ  NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- posts: domain.post 슬라이스. 특정 board에 속함(BOARD-02). FK 컬럼은 UUID 직접(D-70).
CREATE TABLE posts (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    board_id   UUID         NOT NULL,
    author_id  UUID         NOT NULL,
    title      VARCHAR(200) NOT NULL,
    content    TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ  NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- comments: domain.comment 슬라이스. 1단계 대댓글(CMNT-02 — parent_comment_id nullable).
CREATE TABLE comments (
    id                UUID        NOT NULL DEFAULT gen_random_uuid(),
    post_id           UUID        NOT NULL,
    parent_comment_id UUID        NULL,                 -- 1단계 대댓글만(2단계 이상 범위 외)
    author_id         UUID        NOT NULL,
    content           TEXT        NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at        TIMESTAMPTZ NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- reactions: 단순화 스키마(BaseEntity 공통 컬럼 미적용). (user, target)당 1행·멱등(D-73).
-- version/updated_at/deleted_at 없음 — 04-04에서 MyBatis ON CONFLICT upsert·하드 DELETE로만 다룸.
CREATE TABLE reactions (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    target_type VARCHAR(20) NOT NULL,                   -- POST / COMMENT
    target_id   UUID        NOT NULL,
    user_id     UUID        NOT NULL,
    type        VARCHAR(20) NOT NULL,                   -- LIKE / DISLIKE
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    -- P6/D-74: 동시 dup-vote 차단의 DB 레벨 근간. 04-04 ON CONFLICT 대상이므로 컬럼 순서 고정.
    CONSTRAINT ux_reactions_target_user UNIQUE (target_type, target_id, user_id)
);

-- 조회 성능 인덱스: FK 경로 + soft-delete 필터(D-77).
CREATE INDEX ix_posts_board_id ON posts (board_id);
CREATE INDEX ix_comments_post_id ON comments (post_id);
CREATE INDEX ix_comments_parent_comment_id ON comments (parent_comment_id);
CREATE INDEX ix_reactions_target ON reactions (target_type, target_id);
