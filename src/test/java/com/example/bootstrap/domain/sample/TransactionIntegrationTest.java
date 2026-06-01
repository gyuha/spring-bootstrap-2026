package com.example.bootstrap.domain.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.bootstrap.domain.sample.entity.Sample;
import com.example.bootstrap.domain.sample.mapper.SampleQueryMapper;
import com.example.bootstrap.domain.sample.repository.SampleRepository;
import com.example.bootstrap.global.BaseIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JPA 쓰기 + MyBatis 조회가 단일 JpaTransactionManager·동일 커넥션을 공유하는지 검증 (FOUND-02, D-08).
 *
 * <p>한 트랜잭션 안에서 JPA로 저장(아직 미커밋)한 행을 MyBatis 매퍼가 조회로 보면, 둘이 동일
 * DataSource·동일 물리 트랜잭션을 공유함이 증명된다. 롤백 시 MyBatis 조회 결과가 0이 되는지도 확인한다.
 *
 * <p>각 테스트는 {@code @BeforeEach}/{@code @AfterEach}에서 samples 테이블을 TRUNCATE해 독립적으로 실행된다.
 */
class TransactionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    SampleRepository sampleRepository;

    @Autowired
    SampleQueryMapper sampleQueryMapper;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("TRUNCATE TABLE samples");
    }

    @Test
    void jpaSaveIsVisibleToMyBatisWithinSameTransaction() {
        long countSeenInsideTx = transactionTemplate.execute(status -> {
            sampleRepository.saveAndFlush(Sample.create("tx-shared"));
            // 같은 트랜잭션/커넥션을 공유하므로 미커밋 행도 MyBatis가 본다.
            return sampleQueryMapper.countAll();
        });

        assertThat(countSeenInsideTx).isEqualTo(1L);
    }

    @Test
    void rollbackLeavesMyBatisCountAtZero() {
        transactionTemplate.execute(status -> {
            sampleRepository.saveAndFlush(Sample.create("tx-rollback"));
            assertThat(sampleQueryMapper.countAll()).isEqualTo(1L);
            status.setRollbackOnly();
            return null;
        });

        // 롤백 후 커밋된 행이 없으므로 MyBatis 조회는 0이어야 한다.
        assertThat(sampleQueryMapper.countAll()).isZero();
    }
}
