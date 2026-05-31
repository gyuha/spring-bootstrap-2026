package com.anchors.baseline.authorization.application;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 복잡 조회(읽기 모델) 포트 — evaluate 합산·ListObjects 재귀 CTE. 애플리케이션이 정의하고
 * infrastructure 의 MyBatis 어댑터(@Mapper)가 구현한다(헥사고날 — application 은 MyBatis 를 모른다, D-05).
 * SQL(재귀 CTE)은 Wave 2 에서 작성한다.
 */
public interface PermissionReadPort {

    /**
     * 직접 부여 ∪ 소속 그룹 부여 ∪ 조상 리소스 상속을 한 번에 모으는 재귀 CTE 결과.
     */
    List<EffectiveGrantDto> findEffectiveGrants(@Param("userId") long userId,
                                                @Param("resourceId") long resourceId);

    /**
     * 사용자가 주어진 액션으로 접근 가능한 리소스 ID 목록(상위 부여의 하위 상속 포함 — ListObjects).
     */
    List<Long> listAccessibleResourceIds(@Param("userId") long userId,
                                         @Param("action") String action);
}
