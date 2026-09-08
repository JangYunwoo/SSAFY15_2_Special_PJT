package com.gongguhan.domain.groupbuy.entity;

import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_buys")
public class GroupBuy extends BaseTimeEntity {

    public static final String STATUS_RECRUITING = "RECRUITING";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "group_buy_id")
    private Long groupBuyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "product_price", nullable = false)
    private long productPrice;

    @Column(name = "target_participants", nullable = false)
    private int targetParticipants;

    @Column(name = "unit_share_amount", nullable = false)
    private long unitShareAmount;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "return_shipping_fee", insertable = false, updatable = false)
    private Long returnShippingFee;

    @Column(name = "rounding_diff", nullable = false)
    private long roundingDiff;

    @Column(name = "platform_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal platformFeeRate;

    @Column(name = "expected_platform_fee", nullable = false)
    private long expectedPlatformFee;

    @Column(name = "expected_settlement_amount", nullable = false)
    private long expectedSettlementAmount;

    @Column(name = "recruit_start_at", nullable = false)
    private LocalDateTime recruitStartAt;

    @Column(name = "recruit_end_at", nullable = false)
    private LocalDateTime recruitEndAt;

    @Column(name = "current_participants", nullable = false)
    private int currentParticipants;

    @Column(name = "reserved_slots", nullable = false)
    private int reservedSlots;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @Column(name = "shipping_deadline_at")
    private LocalDateTime shippingDeadlineAt;

    @Column(nullable = false, length = 20)
    private String status;

    @Version
    @Column(nullable = false)
    private long version;

    protected GroupBuy() {
    }

    public static GroupBuy create(
            Product product,
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
        groupBuy.product = product;
        groupBuy.sellerId = sellerId;
        groupBuy.productPrice = productPrice;
        groupBuy.targetParticipants = targetParticipants;
        groupBuy.unitShareAmount = unitShareAmount;
        groupBuy.shippingFee = shippingFee;
        groupBuy.roundingDiff = roundingDiff;
        groupBuy.platformFeeRate = platformFeeRate;
        groupBuy.expectedPlatformFee = expectedPlatformFee;
        groupBuy.expectedSettlementAmount = expectedSettlementAmount;
        groupBuy.recruitStartAt = recruitStartAt;
        groupBuy.recruitEndAt = recruitEndAt;
        groupBuy.currentParticipants = 0;
        groupBuy.reservedSlots = 0;
        groupBuy.status = STATUS_RECRUITING;
        return groupBuy;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Product getProduct() {
        return product;
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

    public Long getReturnShippingFee() {
        return returnShippingFee;
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

    public LocalDateTime getRecruitStartAt() {
        return recruitStartAt;
    }

    public LocalDateTime getRecruitEndAt() {
        return recruitEndAt;
    }

    public int getCurrentParticipants() {
        return currentParticipants;
    }

    public int getReservedSlots() {
        return reservedSlots;
    }

    public LocalDateTime getFulfilledAt() {
        return fulfilledAt;
    }

    public LocalDateTime getShippingDeadlineAt() {
        return shippingDeadlineAt;
    }

    public String getStatus() {
        return status;
    }

    public long getVersion() {
        return version;
    }
}
