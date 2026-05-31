package com.anchors.baseline.identity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import lombok.Getter;

/**
 * 이메일 값 객체(D-02) — @Embeddable, 생성 시점 형식 검증.
 * NOT NULL 단일 컬럼이므로 all-null embeddable 함정(Pitfall 1) 비해당.
 * 원시 String 누출 금지 — getValue() 로만 노출, public setter 금지.
 */
@Embeddable
@Getter
public class Email {

    @Column(nullable = false)
    private String value;

    protected Email() {
    }

    public Email(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            throw new IllegalArgumentException("유효하지 않은 이메일 형식입니다: " + value);
        }
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Email other)) {
            return false;
        }
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
