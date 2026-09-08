package com.gongguhan.global.policy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 운영 정책값 한 행.
 *
 * <p>값은 항상 문자열로 보관하고 해석은 {@link PolicyService}가 한다. 컬럼을
 * 타입별로 나누지 않는 편이 관리자 화면(ADM)에서 단일 폼으로 다루기 쉽다.
 *
 * <p>{@code updated_at}은 DB의 {@code ON UPDATE CURRENT_TIMESTAMP}에 맡긴다.
 * 애플리케이션이 값을 써 넣으면 DB 기본값과 어긋나 감사 시각을 신뢰할 수 없게 된다.
 */
@Entity
@Table(name = "platform_policies")
public class PlatformPolicy {

    @Id
    @Column(name = "policy_key", length = 50)
    private String policyKey;

    @Column(name = "policy_value", nullable = false, length = 100)
    private String policyValue;

    @Column(length = 255)
    private String description;

    /** 마지막으로 값을 바꾼 관리자. 시드 행은 null이다. */
    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected PlatformPolicy() {
    }

    public void changeValue(String policyValue, Long updatedBy) {
        this.policyValue = policyValue;
        this.updatedBy = updatedBy;
    }

    public String getPolicyKey() {
        return policyKey;
    }

    public String getPolicyValue() {
        return policyValue;
    }

    public String getDescription() {
        return description;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
