package com.example.bootstrap.domain.sample.entity;

import com.example.bootstrap.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 참조용 도메인 슬라이스 엔티티 (FOUND-01, D-06).
 *
 * <p>{@link BaseEntity}를 상속해 UUIDv7 PK, audit 시각, soft-delete, 낙관적 락을 자동으로 얻는다.
 * 새 도메인은 이 슬라이스(entity/repository/mapper/dto/service/controller)를 복제해 시작한다.
 */
@Entity
@Table(name = "samples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sample extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    private Sample(String title) {
        this.title = title;
    }

    public static Sample create(String title) {
        return new Sample(title);
    }
}
