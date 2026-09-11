package com.gongguhan.domain.returnrequest.entity;

import com.gongguhan.domain.participation.entity.Participation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 반품 한 건의 상태와 금액 스냅샷을 보관한다.
 *
 * <p>금액은 신청 시점에 확정해 둔다. 상품 가격이나 배송비가 나중에 바뀌어도 이미
 * 접수된 반품의 환불액이 변하면 안 되기 때문이다.
 */
@Entity
@Table(name = "return_requests")
public class ReturnRequest {

    public static final String STATUS_REQUESTED = "REQUESTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DISPUTED = "DISPUTED";
    public static final String STATUS_REFUND_REQUESTED = "REFUND_REQUESTED";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_REFUND_FAILED = "REFUND_FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "return_id")
    private Long returnId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participation_id", nullable = false)
    private Participation participation;

    @Column(name = "reason_type", nullable = false, length = 20)
    private String reasonType;

    @Column(name = "reason_detail", length = 500)
    private String reasonDetail;

    @Column(name = "return_shipping_fee", nullable = false)
    private long returnShippingFee;

    @Column(name = "fee_bearer", nullable = false, length = 10)
    private String feeBearer;

    @Column(name = "refund_amount", nullable = false)
    private long refundAmount;

    @Column(name = "post_confirm", nullable = false)
    private boolean postConfirm;

    @Column(name = "admin_review_required", nullable = false)
    private boolean adminReviewRequired;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "auto_approve_at")
    private LocalDateTime autoApproveAt;

    @Column(name = "auto_refund_due_at")
    private LocalDateTime autoRefundDueAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approve_type", length = 10)
    private String approveType;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(name = "auto_refunded", nullable = false)
    private boolean autoRefunded;

    @Column(name = "forced_by")
    private Long forcedBy;

    @Column(name = "forced_at")
    private LocalDateTime forcedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "pickup_zipcode", length = 5)
    private String pickupZipcode;

    @Column(name = "pickup_address1")
    private String pickupAddress1;

    @Column(name = "pickup_address2")
    private String pickupAddress2;

    protected ReturnRequest() {
    }

    public static ReturnRequest request(
            Participation participation,
            String reasonType,
            String reasonDetail,
            long returnShippingFee,
            String feeBearer,
            long refundAmount,
            boolean postConfirm,
            boolean adminReviewRequired,
            LocalDateTime requestedAt,
            LocalDateTime autoApproveAt,
            String pickupZipcode,
            String pickupAddress1,
            String pickupAddress2) {
        ReturnRequest request = new ReturnRequest();
        request.participation = participation;
        request.reasonType = reasonType;
        request.reasonDetail = reasonDetail;
        request.returnShippingFee = returnShippingFee;
        request.feeBearer = feeBearer;
        request.refundAmount = refundAmount;
        request.postConfirm = postConfirm;
        request.adminReviewRequired = adminReviewRequired;
        request.status = STATUS_REQUESTED;
        request.requestedAt = requestedAt;
        request.autoApproveAt = autoApproveAt;
        request.pickupZipcode = pickupZipcode;
        request.pickupAddress1 = pickupAddress1;
        request.pickupAddress2 = pickupAddress2;
        return request;
    }

    public boolean approve(String type, LocalDateTime approvedAt) {
        if (!STATUS_REQUESTED.equals(status)) {
            return false;
        }
        status = STATUS_APPROVED;
        this.approvedAt = approvedAt;
        approveType = type;
        autoRefundDueAt = approvedAt.plusDays(7);
        return true;
    }

    public boolean approveAutomatically(LocalDateTime approvedAt) {
        if (adminReviewRequired || autoApproveAt == null || approvedAt.isBefore(autoApproveAt)) {
            return false;
        }
        return approve("AUTO", approvedAt);
    }

    public boolean reject(String reason) {
        if (!STATUS_REQUESTED.equals(status)) {
            return false;
        }
        status = STATUS_REJECTED;
        rejectReason = reason;
        return true;
    }

    public boolean dispute() {
        if (!STATUS_REJECTED.equals(status)) {
            return false;
        }
        status = STATUS_DISPUTED;
        adminReviewRequired = true;
        return true;
    }

    public boolean requestRefund(boolean automatic, LocalDateTime requestedAt) {
        if (!STATUS_APPROVED.equals(status)) {
            return false;
        }
        status = STATUS_REFUND_REQUESTED;
        if (!automatic) {
            pickedUpAt = requestedAt;
        }
        autoRefunded = automatic;
        return true;
    }

    public boolean completeRefund(LocalDateTime completedAt) {
        if (!STATUS_REFUND_REQUESTED.equals(status)) {
            return false;
        }
        status = STATUS_REFUNDED;
        this.completedAt = completedAt;
        return true;
    }

    public boolean failRefund() {
        if (!STATUS_REFUND_REQUESTED.equals(status)) {
            return false;
        }
        status = STATUS_REFUND_FAILED;
        return true;
    }

    public Long getReturnId() {
        return returnId;
    }

    public Participation getParticipation() {
        return participation;
    }

    public String getReasonType() {
        return reasonType;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public long getReturnShippingFee() {
        return returnShippingFee;
    }

    public String getFeeBearer() {
        return feeBearer;
    }

    public long getRefundAmount() {
        return refundAmount;
    }

    public boolean isPostConfirm() {
        return postConfirm;
    }

    public boolean isAdminReviewRequired() {
        return adminReviewRequired;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getAutoApproveAt() {
        return autoApproveAt;
    }

    public LocalDateTime getAutoRefundDueAt() {
        return autoRefundDueAt;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public String getPickupZipcode() {
        return pickupZipcode;
    }

    public String getPickupAddress1() {
        return pickupAddress1;
    }

    public String getPickupAddress2() {
        return pickupAddress2;
    }
}
