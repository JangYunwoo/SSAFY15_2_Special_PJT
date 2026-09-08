package com.gongguhan.domain.payment.common.entity;

import com.gongguhan.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    protected Payment() {
    }

    private Payment(Long groupBuyId, Long userId, String method, long krwAmount,
                     LocalDateTime slotExpiresAt, String idempotencyKey) {
        this.groupBuyId = groupBuyId;
        this.userId = userId;
        this.method = method;
        this.krwAmount = krwAmount;
        this.slotExpiresAt = slotExpiresAt;
        this.slotMarker = SLOT_MARKER_ACTIVE;
        this.idempotencyKey = idempotencyKey;
        this.status = STATUS_PROCESSING;
    }

    public static Payment prepare(Long groupBuyId, Long userId, String method, long krwAmount,
                                   LocalDateTime slotExpiresAt, String idempotencyKey) {
        return new Payment(groupBuyId, userId, method, krwAmount, slotExpiresAt, idempotencyKey);
    }

    public void markPaid(Long participationId) {
        this.participationId = participationId;
        this.status = STATUS_PAID;
        this.slotMarker = null;
        this.approvedAt = LocalDateTime.now();
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
}
