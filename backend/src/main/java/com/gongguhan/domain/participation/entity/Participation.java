package com.gongguhan.domain.participation.entity;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "participations")
public class Participation {

    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_FULFILLED = "FULFILLED";
    public static final String STATUS_SHIPPING = "SHIPPING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String ACTIVE_MARKER = "Y";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "participation_id")
    private Long participationId;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "share_amount", nullable = false)
    private long shareAmount;

    @Column(name = "shipping_fee", nullable = false)
    private long shippingFee;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "recipient_name", nullable = false, length = 50)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(nullable = false, length = 5)
    private String zipcode;

    @Column(nullable = false)
    private String address1;

    private String address2;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "withdrawal_period_end_at")
    private LocalDateTime withdrawalPeriodEndAt;

    @Column(name = "settlement_eligible_at")
    private LocalDateTime settlementEligibleAt;

    @Column(name = "active_marker", length = 1)
    private String activeMarker;

    protected Participation() {
    }

    private Participation(Long groupBuyId, Long userId, long shareAmount, long shippingFee, long totalAmount,
                           String recipientName, String recipientPhone, String zipcode,
                           String address1, String address2) {
        this.groupBuyId = groupBuyId;
        this.userId = userId;
        this.shareAmount = shareAmount;
        this.shippingFee = shippingFee;
        this.totalAmount = totalAmount;
        this.recipientName = recipientName;
        this.recipientPhone = recipientPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = address2;
        this.status = STATUS_PAID;
        this.joinedAt = LocalDateTime.now();
        this.activeMarker = ACTIVE_MARKER;
    }

    public static Participation create(Long groupBuyId, Long userId, long shareAmount, long shippingFee,
                                        long totalAmount, String recipientName, String recipientPhone,
                                        String zipcode, String address1, String address2) {
        return new Participation(groupBuyId, userId, shareAmount, shippingFee, totalAmount,
                recipientName, recipientPhone, zipcode, address1, address2);
    }

    /** 모집 완료 판정 시 일괄 전환된다 (PAID → FULFILLED). */
    public void markFulfilled() {
        requireStatus(STATUS_PAID, "결제 완료(PAID) 참여만 모집 확정할 수 있습니다.");
        this.status = STATUS_FULFILLED;
    }

    public void markShipping() {
        requireStatus(STATUS_FULFILLED, "모집 확정(FULFILLED) 참여만 발송 처리할 수 있습니다.");
        this.status = STATUS_SHIPPING;
    }

    /**
     * 배송 완료 전환. 청약철회 종료·정산 자격 시각은 정책값(배송완료 + N일)에서 오므로
     * 계산 책임은 호출자(배송 유스케이스)에 있고, 여기서는 함께 기록만 한다.
     */
    public void markDelivered(LocalDateTime deliveredAt, LocalDateTime withdrawalPeriodEndAt,
                               LocalDateTime settlementEligibleAt) {
        requireStatus(STATUS_SHIPPING, "배송 중(SHIPPING) 참여만 배송 완료 처리할 수 있습니다.");
        this.status = STATUS_DELIVERED;
        this.deliveredAt = deliveredAt;
        this.withdrawalPeriodEndAt = withdrawalPeriodEndAt;
        this.settlementEligibleAt = settlementEligibleAt;
    }

    private void requireStatus(String expected, String message) {
        if (!expected.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, message);
        }
    }

    public Long getParticipationId() {
        return participationId;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getActiveMarker() {
        return activeMarker;
    }

    public long getShareAmount() {
        return shareAmount;
    }

    public long getShippingFee() {
        return shippingFee;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public String getZipcode() {
        return zipcode;
    }

    public String getAddress1() {
        return address1;
    }

    public String getAddress2() {
        return address2;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public LocalDateTime getWithdrawalPeriodEndAt() {
        return withdrawalPeriodEndAt;
    }

    public LocalDateTime getSettlementEligibleAt() {
        return settlementEligibleAt;
    }
}
