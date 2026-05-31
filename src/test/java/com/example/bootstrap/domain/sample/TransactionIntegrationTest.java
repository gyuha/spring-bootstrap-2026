package com.example.bootstrap.domain.sample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.bootstrap.global.BaseIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * JPA 쓰기 + MyBatis 조회가 단일 JpaTransactionManager·동일 커넥션을 공유하는지 검증 (FOUND-02).
 *
 * <p>Wave 0 스캐폴드 — placeholder GREEN. SampleService/SampleRepository/SampleQueryMapper는
 * 01-03에서 생성되므로 지금은 @Autowired하지 않는다(컴파일 에러 회피). 실제 트랜잭션 공유·롤백
 * 일관성 assertion도 01-03에서 채운다.
 */
class TransactionIntegrationTest extends BaseIntegrationTest {

    // TODO(01-03): @Autowired SampleService / SampleRepository / SampleQueryMapper 주입 후
    //              JPA save + MyBatis select 단일 트랜잭션 공유 + 롤백 일관성 검증

    @Test
    void placeholder_jpaAndMyBatisShareTransaction() {
        assertTrue(true);
    }
}
