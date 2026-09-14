package com.gongguhan.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 스키마의 created_at / updated_at 패턴을 공통으로 상속한다.
 * JPA 감사(Auditing)는 {@code global.config.JpaConfig}의 @EnableJpaAuditing으로 활성화된다.
 *
 * <p>여기에는 @ColumnDefault를 붙이지 않는다. DDL 기본값이 생기면 H2가 INSERT 시
 * 이 컬럼들을 generated key로 함께 반환해, KeyHolder로 PK를 꺼내는 코드
 * (공동구매 생성 등)가 "multiple keys"로 깨진다. 원시 INSERT 테스트는 이 컬럼들을
 * 직접 명시해야 한다.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
