package com.gongguhan.domain.payment.common.entity;

import com.gongguhan.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment extends BaseTimeEntity {

    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String SLOT_MARKER_ACTIVE = "Y";

    /** {@code payments.method} 값. CHECK 제약 주석과 같은 값이다. */
    public static final String METHOD_CARD = "CARD";
    public static final String METHOD_KAKAOPAY = "KAKAOPAY";
    public static final String METHOD_CRYPTO = "CRYPTO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 결제 시작 시 고른 주소 ID. V22부터 원본 요청 비교/선택 이력으로만 보관한다.
     * 주소록이 삭제되어도 ID를 지우지 않으며 실제 배송 정보는 shippingSnapshot을 사용한다.
     * V3 이전 행의 NULL 주소는 외부 승인 전에 검증하여 잘못된 청구를 차단한다.
     */
    @Column(name = "address_id")
    private Long addressId;

    /** Original prepare request asset; quote creation must not change the retry contract. */
    @Column(name = "prepare_crypto_asset_id")
    private Long prepareCryptoAssetId;
    @Embedded
    private PaymentShippingSnapshot shippingSnapshot;

    /** null(주소 기본값 사용)과 빈 문자열(요청 없음)을 멱등 비교에서도 구분한다. */
    @Column(name = "prepare_delivery_request", length = 100)
    private String prepareDeliveryRequest;

    public void captureShipping(PaymentShippingSnapshot snapshot, String requestedDeliveryRequest) {
        if (shippingSnapshot != null) throw new IllegalStateException("배송지 스냅샷은 변경할 수 없습니다.");
        shippingSnapshot = java.util.Objects.requireNonNull(snapshot);
        prepareDeliveryRequest = requestedDeliveryRequest;
    }

    public PaymentShippingSnapshot getShippingSnapshot() { return shippingSnapshot; }
    public String getPrepareDeliveryRequest() { return prepareDeliveryRequest; }

    @Column(name = "participation_id")
    private Long participationId;

    @Column(nullable = false, length = 20)
    private String method;

    @Column(name = "krw_amount", nullable = false)
    private long krwAmount;

    @Column(name = "slot_expires_at", nullable = false)
    private LocalDateTime slotExpiresAt;

    @Column(name = "slot_marker", length = 1)
    private String slotMarker;

    @Column(name = "idempotency_key", nullable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "card_approval_status", length = 20)
    private String cardApprovalStatus;

    @Column(name = "card_institution_tx_no", length = 20)
    private String cardInstitutionTxNo;

    /** 결제 행 잠금 안에서 변경하고 외부 승인 전에 커밋하는 실행권. */
    public void beginCardApproval(String institutionTxNo) {
        cardApprovalStatus = "PROCESSING";
        cardInstitutionTxNo = institutionTxNo;
    }

    public void recordCardApprovalOutcome(String outcome) { cardApprovalStatus = outcome; }
    public String getCardApprovalStatus() { return cardApprovalStatus; }
    public boolean hasUnresolvedCardApproval() {
        return "PROCESSING".equals(cardApprovalStatus) || "UNKNOWN".equals(cardApprovalStatus);
    }
    public boolean isCardApprovalInFlight() { return "PROCESSING".equals(cardApprovalStatus); }

    protected Payment() {
    }

    private Payment(Long groupBuyId, Long userId, Long addressId, String method, long krwAmount,
                     LocalDateTime slotExpiresAt, String idempotencyKey) {
        this.groupBuyId = groupBuyId;
        this.userId = userId;
        this.addressId = addressId;
        this.method = method;
        this.krwAmount = krwAmount;
        this.slotExpiresAt = slotExpiresAt;
        this.slotMarker = SLOT_MARKER_ACTIVE;
        this.idempotencyKey = idempotencyKey;
        this.status = STATUS_PROCESSING;
    }


    public static Payment prepare(Long groupBuyId, Long userId, Long addressId, String method,
                                   long krwAmount, LocalDateTime slotExpiresAt,
                                   String idempotencyKey) {
        return new Payment(groupBuyId, userId, addressId, method, krwAmount, slotExpiresAt,
                idempotencyKey);
    }

    public static Payment prepare(Long groupBuyId, Long userId, Long addressId, String method,
                                  long krwAmount, LocalDateTime slotExpiresAt, String idempotencyKey,
                                  Long cryptoAssetId) {
        Payment payment = prepare(groupBuyId, userId, addressId, method, krwAmount, slotExpiresAt, idempotencyKey);
        payment.prepareCryptoAssetId = cryptoAssetId;
        return payment;
    }

    public Long getPrepareCryptoAssetId() { return prepareCryptoAssetId; }

    public void markPaid(Long participationId, LocalDateTime approvedAt) {
        if (isCardApprovalInFlight()) { cardApprovalStatus = "COMPLETED"; }
        this.participationId = participationId;
        this.status = STATUS_PAID;
        this.slotMarker = null;
        this.approvedAt = approvedAt;
    }

    public void markFailed(String reason) {
        this.status = STATUS_FAILED;
        this.slotMarker = null;
        this.failReason = reason;
    }

    public void markExpired() {
        this.status = STATUS_EXPIRED;
        this.slotMarker = null;
        this.failReason = "SLOT_EXPIRED";
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(slotExpiresAt);
    }

    public boolean isProcessing() {
        return STATUS_PROCESSING.equals(status);
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAddressId() {
        return addressId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public String getMethod() {
        return method;
    }

    public long getKrwAmount() {
        return krwAmount;
    }

    public LocalDateTime getSlotExpiresAt() {
        return slotExpiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public String getFailReason() {
        return failReason;
    }
}
