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
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "participations")
public class Participation {

    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_FULFILLED = "FULFILLED";
    public static final String STATUS_SHIPPING = "SHIPPING";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_RETURNING = "RETURNING";
    public static final String STATUS_REFUNDING = "REFUNDING";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String CONFIRM_TYPE_MANUAL = "MANUAL";
    public static final String CONFIRM_TYPE_AUTO = "AUTO";
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

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "confirm_type", length = 10)
    private String confirmType;

    // @ColumnDefault: 테스트(H2, ddl-auto)가 엔티티로 DDL을 만들 때도 운영 스키마(V1,
    // DEFAULT FALSE)처럼 기본값이 생겨야 이 컬럼을 모르는 기존 원시 INSERT가 깨지지 않는다.
    @ColumnDefault("false")
    @Column(name = "confirm_notice_agreed", nullable = false)
    private boolean confirmNoticeAgreed;

    @ColumnDefault("false")
    @Column(name = "confirm_hold", nullable = false)
    private boolean confirmHold;

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

    /**
     * 정산 완료 전환 (S15P21B205-81, FR-STL-01~05).
     *
     * <p>정산 배치가 이 참여 건을 지급 명세에 포함하고 판매자에게 보낼 금액을 확정한
     * 뒤 호출합니다. 구매확정된 건만 정산 대상이므로 {@code CONFIRMED} 에서만 옵니다.
     *
     * <p>이중 정산의 최종 방어선은 이 검사가 아니라
     * {@code settlement_items.UNIQUE(participation_id)} 입니다. 배치 두 개가 동시에
     * 돌면 둘 다 이 검사를 통과할 수 있기 때문입니다.
     */
    public void markSettled() {
        requireStatus(STATUS_CONFIRMED, "구매확정(CONFIRMED) 참여만 정산할 수 있습니다.");
        this.status = STATUS_SETTLED;
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

    /**
     * 수동 구매확정 (CFM-01). 반품 제한 고지 동의는 컨트롤러 검증을 거쳐 항상 true로
     * 기록된다. 정산 자격 시각({@code settlementEligibleAt})은 수동 확정과 무관하게
     * 배송완료 + 정책일로 이미 고정되어 있으며 여기서 당기지 않는다(명세 19장).
     */
    public void confirmManually(LocalDateTime confirmedAt) {
        confirm(CONFIRM_TYPE_MANUAL, confirmedAt);
        this.confirmNoticeAgreed = true;
    }

    /** 자동 구매확정 (배송완료 + AUTO_CONFIRM_DAYS 경과 배치). */
    public void confirmAutomatically(LocalDateTime confirmedAt) {
        confirm(CONFIRM_TYPE_AUTO, confirmedAt);
    }

    private void confirm(String confirmType, LocalDateTime confirmedAt) {
        requireStatus(STATUS_DELIVERED, "배송 완료(DELIVERED) 참여만 구매확정할 수 있습니다.");
        if (confirmHold) {
            throw new BusinessException(ErrorCode.PURCHASE_CONFIRM_RETURN_CONFLICT);
        }
        this.status = STATUS_CONFIRMED;
        this.confirmType = confirmType;
        this.confirmedAt = confirmedAt;
    }

    /**
     * 구매자 이의 제기(CFM-02) 접수 - 자동 구매확정을 보류시킨다(FR-CFM-10).
     * confirm_hold 자체가 참여당 1회 접수 마커라 중복 접수도 여기서 걸러진다.
     */
    public void holdConfirmForDispute() {
        if (!STATUS_SHIPPING.equals(status) && !STATUS_DELIVERED.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "배송 중이거나 배송 완료된 참여만 이의를 제기할 수 있습니다.");
        }
        if (confirmHold) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이미 접수된 이의 제기가 있습니다.");
        }
        this.confirmHold = true;
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

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public String getConfirmType() {
        return confirmType;
    }

    public boolean isConfirmNoticeAgreed() {
        return confirmNoticeAgreed;
    }

    public boolean isConfirmHold() {
        return confirmHold;
    }

    /** 반품 접수와 구매확정 배치가 경쟁하지 않도록 같은 트랜잭션에서 보류한다. */
    public void holdConfirmation() {
        confirmHold = true;
    }

    public void releaseConfirmationHold() {
        confirmHold = false;
    }

    public void markReturning() {
        status = STATUS_RETURNING;
    }

    public void markRefunding() {
        status = STATUS_REFUNDING;
    }

    /**
     * 환불로 종료합니다.
     *
     * <p><b>활성 마커를 비웁니다.</b> {@code uk_participation_active(group_buy_id, user_id,
     * active_marker)} 가 "한 사람이 한 공동구매에 유효 참여 하나" 를 보장하는데, 종료된
     * 뒤에도 {@code 'Y'} 로 남으면 전액 환불받은 사람이 같은 공동구매에 다시 참여할 수
     * 없습니다. MySQL 의 UNIQUE 는 NULL 끼리 충돌시키지 않으므로, 비우면 유일성 검사에서
     * 빠집니다 - {@code payments.slot_marker}, {@code refunds.refund_marker} 와 같은
     * 패턴입니다(스키마 주석: "활성 Y, 취소 시 NULL").
     */
    public void markRefunded() {
        status = STATUS_REFUNDED;
        confirmHold = false;
        activeMarker = null;
    }
}
