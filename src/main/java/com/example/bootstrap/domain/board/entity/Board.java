package com.example.bootstrap.domain.board.entity;

import com.example.bootstrap.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 게시판 엔티티 (BOARD-01).
 *
 * <p>{@link BaseEntity}를 상속해 UUIDv7 PK, audit 시각(UTC), soft-delete, 낙관적 락을 자동으로 얻는다.
 * 생성은 ADMIN 전용이며, {@code createdBy}는 생성한 ADMIN의 user UUID다(D-69/D-70). 04-02
 * 서비스가 {@code create(name, createdBy)} 오버로드로 생성자를 기록하고, 이 wave의 스키마/엔티티
 * 정합 테스트는 {@code create(name)}만 사용한다.
 */
@Entity
@Table(name = "boards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Board extends BaseEntity {

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    private Board(String name, UUID createdBy) {
        this.name = name;
        this.createdBy = createdBy;
    }

    public static Board create(String name) {
        return new Board(name, null);
    }

    public static Board create(String name, UUID createdBy) {
        return new Board(name, createdBy);
    }
}
