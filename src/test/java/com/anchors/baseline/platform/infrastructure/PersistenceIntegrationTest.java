package com.anchors.baseline.platform.infrastructure;

import com.anchors.baseline.AbstractIntegrationTest;
import com.anchors.baseline.platform.application.SampleDto;
import com.anchors.baseline.platform.domain.model.SampleEntity;
import com.anchors.baseline.platform.infrastructure.mybatis.SampleMapper;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PLAT-04 / PLAT-05 / D-04: JPA 쓰기 + MyBatis 복잡 조회가 동일 DataSource 의
 * 단일 트랜잭션에서 원자적으로 동작함을 증명하고, Redis 연결 가용성을 검증한다.
 */
class PersistenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EntityManager em;

    @Autowired
    SampleMapper sampleMapper;

    @Autowired
    StringRedisTemplate redisTemplate;

    /**
     * SC#4: JPA persist → flush → MyBatis select 가 단일 트랜잭션에서 보이는지 검증.
     * em.flush() 없이는 MyBatis 가 아직 DB 에 반영되지 않은 데이터를 읽지 못해 빈 결과가 된다.
     */
    @Test
    @Transactional
    void jpaWriteAndMyBatisReadInSingleTransaction() {
        SampleEntity entity = new SampleEntity("test-value-" + System.currentTimeMillis());
        em.persist(entity);
        em.flush(); // 필수: persistence context 를 DB 에 즉시 반영해야 MyBatis 가 읽는다

        List<SampleDto> results = sampleMapper.findAll();

        assertThat(results)
            .extracting(SampleDto::getValue)
            .anyMatch(v -> v.startsWith("test-value-"));
    }

    /**
     * PLAT-05: Redis 연결이 살아있고 read/write 가 동작하는지 검증.
     */
    @Test
    void redisConnectionAlive() {
        redisTemplate.opsForValue().set("test-key", "test-value");
        assertThat(redisTemplate.opsForValue().get("test-key")).isEqualTo("test-value");
    }
}
