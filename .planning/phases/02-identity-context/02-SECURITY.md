---
phase: 2
slug: identity-context
status: verified
threats_open: 0
asvs_level: 1
created: 2026-05-31
---

# Phase 2 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> 등록부는 3개 PLAN의 `<threat_model>` 블록에서 구성됨 (register_authored_at_plan_time: true).
> mitigate 위협은 모두 구현 + 자동화 테스트(19 GREEN)로 완화 검증됨 → CLOSED. accept 위협은 문서화됨.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| application → domain (User 메서드) | linkIdentity/disable 호출 시 도메인 불변식이 강제되는 경계 | 상태 전이·신원 정보 |
| infrastructure (JPA save) → DB (users) | 영속화 시 UNIQUE 제약이 동시성 최종 방어선이 되는 경계 | email/external_id |
| 도메인 이벤트 (UserDisabled) → 컨텍스트 경계 | Phase 4 구독자에게 전달되는 페이로드 경계 | userId(Long) |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02-01 | Tampering | linkIdentity 관리자 필드 덮어쓰기 | mitigate | IDEN-06: 시그니처에 internalNote 미포함(구조적). UserTest.linkIdentityPreservesInternalNote 검증 | closed |
| T-02-02 | Elevation of Privilege | linkIdentity 멱등성 우회(재연결) | mitigate | IDEN-03: status!=INVITED 가드(InvalidStateTransition). UserTest.linkIdentityTwiceIsRejected 검증 | closed |
| T-02-03 | Spoofing | 중복 email 다중 신원 | mitigate | IDEN-05: DB UNIQUE(email,external_id) + 선검사. dbUniqueConstraintRejectsDuplicateEmail 검증 | closed |
| T-02-04 | Information Disclosure | UserDisabled 페이로드 과다 노출 | mitigate | 페이로드=userId(Long)만(§4.1), PII 미포함. record UserDisabled(Long) | closed |
| T-02-05 | Spoofing | invite 중복 email race | mitigate | existsByEmail 선검사(UX) + DB UNIQUE 결정적 방어(D-06). 두 경로 모두 테스트 검증 | closed |
| T-02-06 | Elevation of Privilege | application 서비스 규칙 누수 | mitigate | D-04: 가드는 User 내부, 서비스는 조율만. ArchUnit + 코드리뷰로 강제(누수 0 확인) | closed |
| T-02-07 | Tampering | 어댑터 타입 직접 주입(계층 붕괴) | mitigate | UserRepository 포트만 주입. ArchitectureTest GREEN(application→infra 0건) | closed |
| T-02-08 | Repudiation | UserDisabled 발행 누락 | mitigate | UserLifecycleIT.disablePublishesUserDisabledEventAfterSave 가 발행 단언(회귀 방어, save 후 타이밍) | closed |
| T-02-09 | Tampering | 매핑↔스키마 불일치 영속화 | mitigate | ddl-auto: validate + IT 컨텍스트 부팅이 정합 강제(불일치 시 즉시 RED). 부팅 성공 = 정합 | closed |
| T-02-SC | Tampering | 의존성(공급망) | accept | 신규 외부 패키지 0건 — Phase 1 BOM 재사용. slopcheck 대상 없음 | closed |

*Status: open · closed* / *Disposition: mitigate · accept · transfer*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-SC | 신규 외부 의존성 0건. Phase 1 검증 BOM만 재사용 — 공급망 표면 증가 없음. | gyuha | 2026-05-31 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-05-31 | 10 | 10 | 0 | gsd-secure-phase (plan-time register, mitigations test-verified) |

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] mitigate 위협 완화책이 자동화 테스트(19 GREEN)로 검증됨
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-05-31
