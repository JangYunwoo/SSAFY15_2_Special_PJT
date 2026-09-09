package com.gongguhan.domain.groupbuy.entity;

import com.gongguhan.global.common.BaseTimeEntity;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;

/**
 * 공동구매 슬롯 선점(payment.common)에 필요한 최소 필드만 매핑한다.
 * 공동구매 CRUD(GRP-01~10)는 이 엔티티를 확장해 별도로 구현된다.
 */
@Entity
@Table(name = "group_buys")
public class GroupBuy extends BaseTimeEntity {

    public static final String STATUS_RECRUITING = "RECRUITING";
    public static final String STATUS_FULFILLED = "FULFILLED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_id")
    private Long groupBuyId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "target_participants", nullable = false)
    private int targetParticipants;

    @Column(name = "unit_share_amount", nullable = false)
    private long unitShareAmount;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "current_participants", nullable = false)
    private int currentParticipants;

    @Column(name = "reserved_slots", nullable = false)
    private int reservedSlots;

    @Column(name = "recruit_end_at", nullable = false)
    private LocalDateTime recruitEndAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @Column(name = "shipping_deadline_at")
    private LocalDateTime shippingDeadlineAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Version
    @Column(nullable = false)
    private Long version;

    protected GroupBuy() {
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public int getTargetParticipants() {
        return targetParticipants;
    }

    public long getUnitShareAmount() {
        return unitShareAmount;
    }

    public long getShippingFee() {
        return shippingFee;
    }

    public int getCurrentParticipants() {
        return currentParticipants;
    }

    public int getReservedSlots() {
        return reservedSlots;
    }

    public LocalDateTime getRecruitEndAt() {
        return recruitEndAt;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    public LocalDateTime getShippingDeadlineAt() {
        return shippingDeadlineAt;
    }

    public boolean isRecruiting() {
        return STATUS_RECRUITING.equals(status);
    }

    public void reserveSlot() {
        if (!isRecruiting()) {
            throw new BusinessException(ErrorCode.GROUP_BUY_NOT_RECRUITING);
        }
        if (currentParticipants + reservedSlots >= targetParticipants) {
            throw new BusinessException(ErrorCode.GROUP_BUY_FULL);
        }
        reservedSlots++;
    }

    public void releaseSlot() {
        ensureReservedSlot();
        reservedSlots--;
    }

    public void confirmParticipant() {
        ensureReservedSlot();
        reservedSlots--;
        currentParticipants++;
    }

    public RecruitmentJudgement judgeRecruitment(
            LocalDateTime judgedAt, LocalDateTime calculatedShippingDeadlineAt) {
        if (!isRecruiting()) {
            return RecruitmentJudgement.NONE;
        }
        if (currentParticipants >= targetParticipants) {
            status = STATUS_FULFILLED;
            reservedSlots = 0;
            fulfilledAt = judgedAt;
            shippingDeadlineAt = calculatedShippingDeadlineAt;
            return RecruitmentJudgement.FULFILLED;
        }
        if (!judgedAt.isBefore(recruitEndAt)) {
            status = STATUS_FAILED;
            reservedSlots = 0;
            return RecruitmentJudgement.FAILED;
        }
        return RecruitmentJudgement.NONE;
    }

    public enum RecruitmentJudgement {
        NONE,
        FULFILLED,
        FAILED
    }

    private void ensureReservedSlot() {
        if (reservedSlots <= 0) {
            throw new IllegalStateException("선점된 공동구매 슬롯이 없습니다.");
        }
    }
}
