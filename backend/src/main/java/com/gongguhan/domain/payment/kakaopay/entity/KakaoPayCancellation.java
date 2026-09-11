package com.gongguhan.domain.payment.kakaopay.entity;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 카카오페이 취소 요청의 멱등 상태. 동일 키는 동일 결제·금액에만 재사용할 수 있다. */
@Entity
@Table(name = "kakaopay_cancellations")
public class KakaoPayCancellation {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    @Id
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false, length = 100)
    private String tid;

    @Column(name = "requested_amount", nullable = false)
    private long requestedAmount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "cancel_aid", length = 100)
    private String cancelAid;

    @Column(name = "approved_cancel_amount")
    private Long approvedCancelAmount;

    @Column(name = "canceled_amount")
    private Long canceledAmount;

    @Column(name = "cancel_available_amount")
    private Long cancelAvailableAmount;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected KakaoPayCancellation() {
    }

    public static KakaoPayCancellation start(String idempotencyKey, Long paymentId,
                                              String tid, long requestedAmount,
                                              LocalDateTime createdAt) {
        KakaoPayCancellation cancellation = new KakaoPayCancellation();
        cancellation.idempotencyKey = idempotencyKey;
        cancellation.paymentId = paymentId;
        cancellation.tid = tid;
        cancellation.requestedAmount = requestedAmount;
        cancellation.status = STATUS_PENDING;
        cancellation.createdAt = createdAt;
        cancellation.updatedAt = createdAt;
        return cancellation;
    }

    public void verifySameRequest(Long expectedPaymentId, long expectedAmount) {
        if (!paymentId.equals(expectedPaymentId) || requestedAmount != expectedAmount) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "동일한 멱등 키를 다른 카카오페이 취소 요청에 사용할 수 없습니다.");
        }
    }

    public void markSucceeded(String aid, long approvedAmount, long canceledAmount,
                              long availableAmount, LocalDateTime completedAt) {
        status = STATUS_SUCCEEDED;
        cancelAid = aid;
        approvedCancelAmount = approvedAmount;
        this.canceledAmount = canceledAmount;
        cancelAvailableAmount = availableAmount;
        failureReason = null;
        updatedAt = completedAt;
    }

    public void markFailed(String reason, LocalDateTime failedAt) {
        status = STATUS_FAILED;
        failureReason = trim(reason);
        updatedAt = failedAt;
    }

    public void markUnknown(String reason, LocalDateTime failedAt) {
        status = STATUS_UNKNOWN;
        failureReason = trim(reason);
        updatedAt = failedAt;
    }

    public boolean isSucceeded() {
        return STATUS_SUCCEEDED.equals(status);
    }

    public boolean isInFlightOrUnknown() {
        return STATUS_PENDING.equals(status) || STATUS_UNKNOWN.equals(status);
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getTid() {
        return tid;
    }

    public long getRequestedAmount() {
        return requestedAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getCancelAid() {
        return cancelAid;
    }

    public Long getApprovedCancelAmount() {
        return approvedCancelAmount;
    }

    public Long getCanceledAmount() {
        return canceledAmount;
    }

    public Long getCancelAvailableAmount() {
        return cancelAvailableAmount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    private String trim(String value) {
        if (value == null || value.isBlank()) {
            return "카카오페이 취소 처리에 실패했습니다.";
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
