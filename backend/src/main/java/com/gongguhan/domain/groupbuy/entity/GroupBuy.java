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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

/**
 * 공동구매의 생성 시점 금액 스냅샷과 모집 상태를 함께 관리한다.
 *
 * <p>분담금·수수료·정산 예상액은 생성 이후 원상품 가격이 바뀌어도 달라지지 않는다.
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

    @ColumnDefault("0")
    @Column(name = "product_price", nullable = false)
    private long productPrice;

    @Column(name = "target_participants", nullable = false)
    private int targetParticipants;

    @Column(name = "unit_share_amount", nullable = false)
    private long unitShareAmount;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @ColumnDefault("0")
    @Column(name = "rounding_diff", nullable = false)
    private long roundingDiff;

    @ColumnDefault("0.0500")
    @Column(name = "platform_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal platformFeeRate;

    @ColumnDefault("0")
    @Column(name = "expected_platform_fee", nullable = false)
    private long expectedPlatformFee;

    @ColumnDefault("0")
    @Column(name = "expected_settlement_amount", nullable = false)
    private long expectedSettlementAmount;

    @Column(name = "current_participants", nullable = false)
    private int currentParticipants;

    @Column(name = "reserved_slots", nullable = false)
    private int reservedSlots;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "recruit_start_at", nullable = false)
    private LocalDateTime recruitStartAt;

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

    public static GroupBuy create(
            Long productId,
            Long sellerId,
            long productPrice,
            int targetParticipants,
            long unitShareAmount,
            long shippingFee,
            long roundingDiff,
            BigDecimal platformFeeRate,
            long expectedPlatformFee,
            long expectedSettlementAmount,
            LocalDateTime recruitStartAt,
            LocalDateTime recruitEndAt) {
        GroupBuy groupBuy = new GroupBuy();
        groupBuy.productId = productId;
        groupBuy.sellerId = sellerId;
        groupBuy.productPrice = productPrice;
        groupBuy.targetParticipants = targetParticipants;
        groupBuy.unitShareAmount = unitShareAmount;
        groupBuy.shippingFee = shippingFee;
        groupBuy.roundingDiff = roundingDiff;
        groupBuy.platformFeeRate = platformFeeRate;
        groupBuy.expectedPlatformFee = expectedPlatformFee;
        groupBuy.expectedSettlementAmount = expectedSettlementAmount;
        groupBuy.currentParticipants = 0;
        groupBuy.reservedSlots = 0;
        groupBuy.recruitStartAt = recruitStartAt;
        groupBuy.recruitEndAt = recruitEndAt;
        groupBuy.status = STATUS_RECRUITING;
        return groupBuy;
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

    public long getProductPrice() {
        return productPrice;
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

    public long getReturnShippingFee() {
        return shippingFee * 2;
    }

    public long getRoundingDiff() {
        return roundingDiff;
    }

    public BigDecimal getPlatformFeeRate() {
        return platformFeeRate;
    }

    public long getExpectedPlatformFee() {
        return expectedPlatformFee;
    }

    public long getExpectedSettlementAmount() {
        return expectedSettlementAmount;
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

    public LocalDateTime getRecruitStartAt() {
        return recruitStartAt;
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

    /** 참여나 유효 결제 슬롯이 생기기 전까지만 모집 조건을 바꿀 수 있다. */
    public boolean canEditRecruitment() {
        return isRecruiting() && currentParticipants == 0 && reservedSlots == 0;
    }

    public void updateRecruitment(
            Long productId,
            long productPrice,
            int targetParticipants,
            long unitShareAmount,
            long shippingFee,
            long roundingDiff,
            BigDecimal platformFeeRate,
            long expectedPlatformFee,
            long expectedSettlementAmount,
            LocalDateTime recruitStartAt,
            LocalDateTime recruitEndAt) {
        if (!canEditRecruitment()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "참여자나 진행 중인 결제 슬롯이 없는 모집 중 공동구매만 수정할 수 있습니다.");
        }
        this.productId = productId;
        this.productPrice = productPrice;
        this.targetParticipants = targetParticipants;
        this.unitShareAmount = unitShareAmount;
        this.shippingFee = shippingFee;
        this.roundingDiff = roundingDiff;
        this.platformFeeRate = platformFeeRate;
        this.expectedPlatformFee = expectedPlatformFee;
        this.expectedSettlementAmount = expectedSettlementAmount;
        this.recruitStartAt = recruitStartAt;
        this.recruitEndAt = recruitEndAt;
    }

    /** 참여자가 전혀 없는 모집 건만 일반 취소할 수 있다. */
    public void cancelBeforeParticipation() {
        if (!canEditRecruitment()) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "참여자나 진행 중인 결제 슬롯이 없는 모집 중 공동구매만 취소할 수 있습니다.");
        }
        status = "CANCELED";
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
