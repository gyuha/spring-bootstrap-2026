package com.anchors.baseline.authorization.application;

import com.anchors.baseline.authorization.domain.model.Action;
import com.anchors.baseline.authorization.domain.model.ResourceId;
import java.util.List;

/**
 * 인가 판정·조회의 추상 경계(A-5 / SC#5 / AUTHZ-08). 외부 엔진(OpenFGA 등)으로 교체 가능한
 * 연산만 노출한다 — 부여/회수(grant/revoke)는 포트에 두지 않고 도메인 애그리거트 쓰기로 처리한다(D-04).
 * Postgres 내부 어댑터는 Wave 2 에서 구현한다.
 */
public interface AuthorizationPort {

    boolean evaluate(long userId, ResourceId resource, Action action);

    List<Long> listObjects(long userId, Action action);
}
