---
phase: 1
slug: platform-skeleton
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-30
---

# Phase 1 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> 등록부는 5개 PLAN의 `<threat_model>` 블록에서 구성됨 (register_authored_at_plan_time: true). 전 항목 disposition=accept, 문서화된 수용 리스크로 CLOSED.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| build.gradle.kts → Maven Central | 의존성 다운로드 시 외부 레지스트리 접근 | 패키지 아티팩트 |
| compose.yaml → Docker Hub | 컨테이너 이미지 다운로드 | 이미지 레이어 |
| interfaces(REST) → application | 외부 HTTP 요청이 애플리케이션 계층으로 진입 | 요청 바디(value) |
| application.yml → 로컬 PostgreSQL | 자격증명 포함 설정 | DB 자격증명(로컬 전용) |
| TestRestTemplate → /actuator/health | 테스트 클라이언트가 내부 헬스 엔드포인트 접근 | 헬스 상태 상세 |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-01-01 | Tampering | 의존성 다운로드(Maven Central) | accept | RESEARCH Package Legitimacy Audit [VERIFIED: Maven Central] | closed |
| T-01-SC | Tampering | npm/pip/cargo 설치 | accept | Java/Gradle 의존성만 사용, Audit 완료 | closed |
| T-02-01 | Information Disclosure | application-test.yml | accept | 테스트 전용 설정, 운영 자격증명 없음 | closed |
| T-02-SC | Tampering | Testcontainers 이미지 다운로드 | accept | postgres:16/redis:7 공식 이미지, 테스트 한정 | closed |
| T-03-01 | Information Disclosure | actuator show-details: always | accept (dev) | 개발 전용 주석 명시; 운영 배포 시 when_authorized (AR-01) | closed |
| T-03-02 | Information Disclosure | compose POSTGRES_PASSWORD=baseline | accept (local) | 로컬 개발 전용 자격증명, 파일 주석 명시 (AR-02) | closed |
| T-03-SC | Tampering | Docker Hub 이미지 | accept | 공식 이미지 | closed |
| T-04-01 | Elevation of Privilege | SampleController REST API | accept (dev) | Phase 1 골격, 인증/인가는 Phase 3·4 (AR-03) | closed |
| T-04-02 | Tampering | SampleEntity value 입력 | accept | 검증용 엔티티, 입력검증은 Phase 2+ 업무 도메인 (AR-04) | closed |
| T-04-SC | Tampering | 의존성 | accept | 기존 검증 의존성만, 신규 패키지 없음 | closed |
| T-05-01 | Information Disclosure | actuator show-details (test) | accept (dev) | 테스트 환경 전용 (AR-01과 동일) | closed |
| T-05-SC | Tampering | Testcontainers 컨테이너 기동 | accept | 공식 이미지, 테스트 한정 | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-03-01, T-05-01 | actuator `show-details: always`는 개발/테스트 전용. 운영 배포 시 `when_authorized`로 변경 필요. | gyuha | 2026-05-30 |
| AR-02 | T-03-02 | compose `POSTGRES_PASSWORD=baseline`은 로컬 개발 전용. 운영은 별도 시크릿 관리 레이어 필요. | gyuha | 2026-05-30 |
| AR-03 | T-04-01 | `SampleController`는 인증 없음. Phase 3·4에서 인증/인가 추가 전까지 운영 비노출. | gyuha | 2026-05-30 |
| AR-04 | T-04-02 | `SampleEntity.value` 입력 검증 없음. 입력 검증은 Phase 2+ 업무 도메인에서 적용. | gyuha | 2026-05-30 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-30 | 12 | 12 | 0 | gsd-secure-phase (plan-time register, short-circuit) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-30
