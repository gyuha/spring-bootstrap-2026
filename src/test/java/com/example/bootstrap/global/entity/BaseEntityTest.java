package com.example.bootstrap.global.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.bootstrap.domain.sample.entity.Sample;
import com.example.bootstrap.domain.sample.repository.SampleRepository;
import com.example.bootstrap.global.config.JpaConfig;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BaseEntity audit/soft-delete/낙관적 락/UTC 라운드트립 검증 (FOUND-03).
 *
 * <p>JVM 타임존을 비-UTC(Asia/Seoul)로 강제해 {@code hibernate.jdbc.time_zone=UTC}(D-13)가
 * Instant 라운드트립을 보장하는지 실측한다. 타임존은 Hibernate 부팅 전에 설정되어야 하므로
 * static 초기화 블록에서 지정한다.
 */
@DataJpaTest
@Import(JpaConfig.class)
@Testcontainers
class BaseEntityTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    SampleRepository sampleRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void auditTimestampsArePopulatedAsUtcInstant() {
        Sample saved = sampleRepository.saveAndFlush(Sample.create("audit"));

        assertThat(saved.getCreatedAt()).isNotNull().isInstanceOf(Instant.class);
        assertThat(saved.getUpdatedAt()).isNotNull().isInstanceOf(Instant.class);
    }

    @Test
    void softDeletedRowIsExcludedBySqlRestriction() {
        Sample saved = sampleRepository.saveAndFlush(Sample.create("soft-delete"));
        UUID id = saved.getId();

        saved.softDelete();
        sampleRepository.saveAndFlush(saved);
        entityManager.clear();

        assertThat(sampleRepository.findById(id)).isEmpty();
    }

    @Test
    void versionStartsAtZeroAndIncrementsOnUpdate() {
        Sample saved = sampleRepository.saveAndFlush(Sample.create("v0"));
        assertThat(saved.getVersion()).isZero();

        saved.softDelete();
        Sample updated = sampleRepository.saveAndFlush(saved);

        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void optimisticLockConflictThrows() {
        Sample saved = sampleRepository.saveAndFlush(Sample.create("lock"));
        UUID id = saved.getId();
        entityManager.clear();

        // 같은 버전(0)을 가진 두 인스턴스를 분리 로드해 동시 수정 충돌을 유발한다.
        Sample first = sampleRepository.findById(id).orElseThrow();
        entityManager.detach(first);
        Sample second = sampleRepository.findById(id).orElseThrow();

        second.softDelete();
        sampleRepository.saveAndFlush(second);
        entityManager.clear();

        first.softDelete();
        assertThatThrownBy(() -> sampleRepository.saveAndFlush(first))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void instantRoundTripsUnderNonUtcJvmTimezone() {
        assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");

        Sample saved = sampleRepository.saveAndFlush(Sample.create("utc-roundtrip"));
        Instant createdAt = saved.getCreatedAt();
        UUID id = saved.getId();
        entityManager.clear();

        Sample reread = sampleRepository.findById(id).orElseThrow();

        assertThat(reread.getCreatedAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(createdAt.truncatedTo(ChronoUnit.MILLIS));
    }
}
