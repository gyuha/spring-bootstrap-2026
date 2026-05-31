-- Authorization 컨텍스트 — 3계층 권한(전역 역할/메뉴/리소스) + 그룹 + 리소스 계층 상속 (Phase 4)
-- soft reference: no physical FK (§4.1 / D-07) — user_id/group_id/resource_id/menu_id 는 다른
--   컨텍스트(identity 등)로의 논리 참조이며 물리 외래키 제약을 걸지 않는다(컨텍스트 독립).
-- created_at: 단일 소스 — 엔티티가 Instant.now() 로 소유한다(D-09). DDL 기본값 절은 생략한다.

-- AUTHZ-01: 전역 역할 부여/회수 — userId 에 전역 역할 부여
CREATE TABLE IF NOT EXISTS global_role_grants (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    role       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (user_id, role)
);
CREATE INDEX IF NOT EXISTS idx_global_role_grants_user_id ON global_role_grants (user_id);
COMMENT ON TABLE global_role_grants IS 'AUTHZ-01 전역 역할 부여 — user_id soft ref(FK 없음)';

-- AUTHZ-02: 사용자/그룹 메뉴 권한 — 주체(user XOR group) polymorphic 단일 테이블
CREATE TABLE IF NOT EXISTS menu_grants (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NULL,
    group_id   BIGINT       NULL,
    menu_id    BIGINT       NOT NULL,
    role       VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NOT NULL) <> (group_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_menu_grants_user_id ON menu_grants (user_id);
CREATE INDEX IF NOT EXISTS idx_menu_grants_group_id ON menu_grants (group_id);
COMMENT ON TABLE menu_grants IS 'AUTHZ-02 메뉴 권한 — 주체 user XOR group polymorphic, soft ref';

-- AUTHZ-03/06: 사용자/그룹 리소스 권한 — 주체(user XOR group) polymorphic 단일 테이블
CREATE TABLE IF NOT EXISTS resource_grants (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NULL,
    group_id    BIGINT       NULL,
    resource_id BIGINT       NOT NULL,
    role        VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((user_id IS NOT NULL) <> (group_id IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_resource_grants_user_id ON resource_grants (user_id);
CREATE INDEX IF NOT EXISTS idx_resource_grants_group_id ON resource_grants (group_id);
CREATE INDEX IF NOT EXISTS idx_resource_grants_resource_id ON resource_grants (resource_id);
COMMENT ON TABLE resource_grants IS 'AUTHZ-03/06 리소스 권한 — 주체 user XOR group polymorphic, soft ref';

-- AUTHZ-04: 그룹
CREATE TABLE IF NOT EXISTS groups (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
COMMENT ON TABLE groups IS 'AUTHZ-04 그룹 — 앱 자체 관리(§5.5)';

-- AUTHZ-04: 그룹 멤버십(다대다, ID 참조)
CREATE TABLE IF NOT EXISTS group_members (
    id         BIGSERIAL PRIMARY KEY,
    group_id   BIGINT NOT NULL,
    user_id    BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_group_members_group_id ON group_members (group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_user_id ON group_members (user_id);
COMMENT ON TABLE group_members IS 'AUTHZ-04 그룹 멤버십 — group_id/user_id soft ref(FK 없음)';

-- AUTHZ-05: 리소스 계층 — 재귀 CTE 상속 전개 대상
CREATE TABLE IF NOT EXISTS resource_hierarchy (
    id                 BIGSERIAL PRIMARY KEY,
    resource_id        BIGINT NOT NULL,
    parent_resource_id BIGINT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (resource_id)
);
CREATE INDEX IF NOT EXISTS idx_resource_hierarchy_resource_id ON resource_hierarchy (resource_id);
CREATE INDEX IF NOT EXISTS idx_resource_hierarchy_parent_resource_id ON resource_hierarchy (parent_resource_id);
COMMENT ON TABLE resource_hierarchy IS 'AUTHZ-05 리소스 계층 — 재귀 CTE 상속 전개 대상, resource_id soft ref';
