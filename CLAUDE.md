<!-- GSD:project-start source:PROJECT.md -->
## Project

**Spring DDD 백엔드 베이스라인**

`spring-backend-ddd-baseline.md`에 고정된 표준을 실제 코드로 구현한, 재사용 가능한 Spring 백엔드 골격이다. Java 21 + Spring Boot MVC, JPA(쓰기) + MyBatis(복잡 조회) 이원화, PostgreSQL, Redis, Flyway 위에 헥사고날 + DDD 패키지 구조를 갖추고, 거의 모든 업무 시스템에서 재사용되는 기반 컨텍스트(식별/인증/인가)를 제공한다. 이후 프로젝트는 이 골격을 복제해 자신의 업무 도메인만 채워 넣는다.

**Core Value:** 새 백엔드를 시작할 때마다 스택·아키텍처·인증/인가를 다시 결정하지 않도록, 검증된 DDD 골격과 식별/인증/인가 기반을 그대로 가져다 쓸 수 있게 한다.

### Constraints

- **Tech stack**: Java 21 / Spring Boot + MVC / Spring Data JPA + MyBatis / PostgreSQL / Redis / Flyway / Spring Security OAuth2-OIDC / Lombok — 베이스라인 2장에 고정. 임의 대체 금지
- **Architecture**: MVC + 가상 스레드(WebFlux 아님), JPA=쓰기·MyBatis=복잡 조회, BFF 인증(토큰 브라우저 비노출), 인증/인가 분리, 인가=포트/어댑터, 불변 식별자 매칭 — A-1~A-6
- **DDD**: 모든 기능은 도메인 모델에서 출발(테이블 우선 금지). 바운디드 컨텍스트 → 애그리거트/VO/불변식 → 영속성 순. JPA 방식 A(실용형) 기본, 컨텍스트별 B 선택 가능
- **Persistence integrity**: 하나의 트랜잭션 = 하나의 애그리거트 수정. 컨텍스트 간 변경은 도메인 이벤트 + 최종적 일관성
- **Security**: BFF 패턴, 액세스 토큰은 Redis 서버 세션에만 보관 (NFR-02)
<!-- GSD:project-end -->

<!-- GSD:stack-start source:STACK.md -->
## Technology Stack

Technology stack not yet documented. Will populate after codebase mapping or first phase.
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
