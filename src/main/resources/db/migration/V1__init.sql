-- =============================================================================
-- V1__init.sql — 초기 스키마 (Account 도메인 + AI 도메인)
--
-- 포함 테이블:
--   [Account 도메인]
--   users                        : 사용자 계정 (이메일/비밀번호 및 OAuth2 공용)
--   refresh_tokens               : Refresh Token Rotation 관리
--   oauth_accounts               : OAuth2 소셜 계정 연동 (Google, Kakao)
--   [AI 도메인]
--   ai_chat_sessions             : AI 채팅 세션 (사용자별 대화 그룹)
--   ai_chat_messages             : AI 채팅 메시지 (세션 내 개별 메시지)
--
-- 주의: Spring Batch 메타데이터 테이블은 V2__batch_schema.sql 에서 생성합니다.
--       spring.batch.jdbc.initialize-schema=never 로 설정하여 Flyway가 배치
--       메타데이터 테이블 생성을 전담합니다.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. users
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE users (
    id                BIGSERIAL       PRIMARY KEY,
    email             VARCHAR(255)    NOT NULL,
    password          VARCHAR(255)    NULL,           -- OAuth2 전용 계정은 NULL
    nickname          VARCHAR(100)    NOT NULL,
    role              VARCHAR(20)     NOT NULL DEFAULT 'USER',
    email_verified    BOOLEAN         NOT NULL DEFAULT TRUE,
    profile_image_url VARCHAR(500)    NULL,
    created_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);

COMMENT ON TABLE  users                  IS '사용자 계정';
COMMENT ON COLUMN users.id               IS 'PK (auto-generated)';
COMMENT ON COLUMN users.email            IS '이메일 주소 (로그인 식별자, unique)';
COMMENT ON COLUMN users.password         IS 'BCrypt 인코딩된 비밀번호 (OAuth2 전용 계정은 NULL)';
COMMENT ON COLUMN users.nickname         IS '사용자 닉네임';
COMMENT ON COLUMN users.role             IS '권한 역할 (USER | ADMIN)';
COMMENT ON COLUMN users.email_verified   IS '이메일 인증 여부 (가입 즉시 TRUE)';
COMMENT ON COLUMN users.profile_image_url IS '프로필 이미지 URL (OAuth2 provider 제공)';
COMMENT ON COLUMN users.created_at       IS '생성일시';
COMMENT ON COLUMN users.updated_at       IS '수정일시';

-- 이메일 검색 인덱스 (UNIQUE 제약이 이미 인덱스를 생성하므로 role 복합 인덱스만 추가)
CREATE INDEX idx_users_role ON users (role);

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. refresh_tokens
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE refresh_tokens (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token      VARCHAR(512) NOT NULL,
    expired_at TIMESTAMP    NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE  refresh_tokens            IS 'Refresh Token 발급 이력 (Rotation 방식)';
COMMENT ON COLUMN refresh_tokens.id         IS 'PK (auto-generated)';
COMMENT ON COLUMN refresh_tokens.user_id    IS 'Account FK';
COMMENT ON COLUMN refresh_tokens.token      IS 'Refresh Token 값';
COMMENT ON COLUMN refresh_tokens.expired_at IS '만료일시 (발급 후 14일)';
COMMENT ON COLUMN refresh_tokens.created_at IS '발급일시';

-- 사용자별 토큰 조회 / 만료 토큰 일괄 삭제 Batch
CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expired_at ON refresh_tokens (expired_at);

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. oauth_accounts
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE oauth_accounts (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    provider    VARCHAR(50) NOT NULL,          -- 'google' | 'kakao'
    provider_id VARCHAR(255) NOT NULL,         -- provider 측 고유 사용자 ID
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_oauth_accounts_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT chk_oauth_accounts_provider   CHECK (provider IN ('google', 'kakao')),
    CONSTRAINT fk_oauth_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE  oauth_accounts             IS 'OAuth2 소셜 계정 연동 정보';
COMMENT ON COLUMN oauth_accounts.id          IS 'PK (auto-generated)';
COMMENT ON COLUMN oauth_accounts.user_id     IS 'Account FK (1:N)';
COMMENT ON COLUMN oauth_accounts.provider    IS 'OAuth2 provider 식별자 (google | kakao)';
COMMENT ON COLUMN oauth_accounts.provider_id IS 'provider 측 고유 사용자 ID';
COMMENT ON COLUMN oauth_accounts.created_at  IS '소셜 계정 연동일시';

-- 사용자별 소셜 계정 목록 조회
CREATE INDEX idx_oauth_accounts_user_id ON oauth_accounts (user_id);

-- =============================================================================
-- AI 도메인 스키마
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. ai_chat_sessions
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE ai_chat_sessions (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    title      VARCHAR(255) NULL,                -- 세션 제목 (미지정 시 NULL)
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ai_chat_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

COMMENT ON TABLE  ai_chat_sessions            IS 'AI 채팅 세션 (사용자별 대화 그룹)';
COMMENT ON COLUMN ai_chat_sessions.id         IS 'PK (auto-generated)';
COMMENT ON COLUMN ai_chat_sessions.user_id    IS 'Account FK (users.id)';
COMMENT ON COLUMN ai_chat_sessions.title      IS '세션 제목 (생략 시 NULL)';
COMMENT ON COLUMN ai_chat_sessions.created_at IS '세션 생성일시';
COMMENT ON COLUMN ai_chat_sessions.updated_at IS '세션 최종 수정일시';

-- 사용자별 세션 목록 조회 / 최근 활동 순 정렬
CREATE INDEX idx_ai_chat_sessions_user_id   ON ai_chat_sessions (user_id);
CREATE INDEX idx_ai_chat_sessions_updated_at ON ai_chat_sessions (updated_at DESC);

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. ai_chat_messages
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE ai_chat_messages (
    id         BIGSERIAL   PRIMARY KEY,
    session_id BIGINT      NOT NULL,
    role       VARCHAR(20) NOT NULL,             -- 'user' | 'assistant' | 'system'
    content    TEXT        NOT NULL,
    model      VARCHAR(100) NULL,                -- AI 응답 시 사용된 모델명 (user 메시지는 NULL)
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_ai_chat_messages_role CHECK (role IN ('user', 'assistant', 'system')),
    CONSTRAINT fk_ai_chat_messages_session
        FOREIGN KEY (session_id) REFERENCES ai_chat_sessions (id) ON DELETE CASCADE
);

COMMENT ON TABLE  ai_chat_messages            IS 'AI 채팅 메시지 (세션 내 개별 메시지)';
COMMENT ON COLUMN ai_chat_messages.id         IS 'PK (auto-generated)';
COMMENT ON COLUMN ai_chat_messages.session_id IS 'AI 채팅 세션 FK (ai_chat_sessions.id)';
COMMENT ON COLUMN ai_chat_messages.role       IS '메시지 역할 (user | assistant | system)';
COMMENT ON COLUMN ai_chat_messages.content    IS '메시지 본문';
COMMENT ON COLUMN ai_chat_messages.model      IS 'AI 응답에 사용된 모델명 (기본 gpt-4o-mini, user 메시지는 NULL)';
COMMENT ON COLUMN ai_chat_messages.created_at IS '메시지 생성일시';

-- 세션 내 메시지 시계열 조회 / 세션-역할 복합 검색
CREATE INDEX idx_ai_chat_messages_session_id         ON ai_chat_messages (session_id);
CREATE INDEX idx_ai_chat_messages_session_created_at ON ai_chat_messages (session_id, created_at);

-- Spring Batch 스키마는 V2__batch_schema.sql 에서 관리합니다.
