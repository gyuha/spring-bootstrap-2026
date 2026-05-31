-- Authorization 멱등 부여 제약 — resource_grants/menu_grants 중복 부여 차단 (Phase 4 코드 리뷰 후속)
-- V3 는 이미 적용된 마이그레이션이므로 편집하지 않고(불변성), 추가 제약을 본 V4 로 분리한다.
-- 런타임 요구: PostgreSQL 14+ — 재귀 CTE 사이클 가드(CYCLE ... SET ... USING path, AuthorizationMapper.xml)는
--   PG14 미만에서 문법 에러로 하드 실패한다. 운영 DB 도 PG14+ 를 보장해야 한다.
-- polymorphic(user XOR group) 테이블이라 NULL 컬럼이 기본 UNIQUE 의 NULL-distinct 규칙을 우회하므로,
--   주체별 부분 UNIQUE 인덱스로 분리한다(PG12+). 동일 (주체, 대상, role) 중복 행을 차단한다.

-- AUTHZ-02: menu_grants 멱등 부여
CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_grants_user ON menu_grants (user_id, menu_id, role) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_menu_grants_group ON menu_grants (group_id, menu_id, role) WHERE group_id IS NOT NULL;

-- AUTHZ-03/06: resource_grants 멱등 부여
CREATE UNIQUE INDEX IF NOT EXISTS uq_resource_grants_user ON resource_grants (user_id, resource_id, role) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_resource_grants_group ON resource_grants (group_id, resource_id, role) WHERE group_id IS NOT NULL;
