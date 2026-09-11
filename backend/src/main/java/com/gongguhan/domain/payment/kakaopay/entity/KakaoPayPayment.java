package com.gongguhan.domain.payment.kakaopay.entity;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 카카오페이 결제 준비·승인 결과. 공통 payments 행과 같은 PK를 사용한다. */
@Entity
@Table(name = "kakaopay_payments")
public class KakaoPayPayment {

    @Id
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(length = 100)
    private String tid;

    @Column(length = 100)
    private String aid;

    @Column(name = "partner_order_id", length = 100)
    private String partnerOrderId;

    @Column(name = "partner_user_id", length = 100)
    private String partnerUserId;

    @Column(name = "total_amount")
    private Long totalAmount;

    @Column(name = "tax_free_amount", nullable = false)
    private long taxFreeAmount;

    @Column(name = "vat_amount")
    private Long vatAmount;

    @Column(name = "payment_method_type", length = 20)
    private String paymentMethodType;

    @Column(name = "next_redirect_pc_url", length = 500)
    private String nextRedirectPcUrl;

    @Column(name = "state_nonce", length = 64)
    private String stateNonce;

    @Column(name = "state_expires_at")
    private LocalDateTime stateExpiresAt;

    @Column(name = "state_consumed_at")
    private LocalDateTime stateConsumedAt;

    protected KakaoPayPayment() {
    }

    private KakaoPayPayment(Long paymentId) {
        this.paymentId = paymentId;
        this.taxFreeAmount = 0;
    }

    public static KakaoPayPayment forPayment(Long paymentId) {
        return new KakaoPayPayment(paymentId);
    }

    public void recordReady(String tid, String partnerOrderId, String partnerUserId,
                            long totalAmount, String nextRedirectPcUrl, String stateNonce,
                            LocalDateTime stateExpiresAt) {
        this.tid = tid;
        this.partnerOrderId = partnerOrderId;
        this.partnerUserId = partnerUserId;
        this.totalAmount = totalAmount;
        this.nextRedirectPcUrl = nextRedirectPcUrl;
        this.stateNonce = stateNonce;
        this.stateExpiresAt = stateExpiresAt;
        this.stateConsumedAt = null;
    }

    public void verifyReady(String nonce) {
        if (tid == null || stateNonce == null || stateConsumedAt != null
                || !stateNonce.equals(nonce)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "카카오페이 승인 정보가 만료되었거나 이미 사용되었습니다.");
        }
    }

    public void verifyApproval(String expectedTid, String expectedOrderId,
                               String expectedUserId, long expectedAmount) {
        if (!tid.equals(expectedTid)
                || !partnerOrderId.equals(expectedOrderId)
                || !partnerUserId.equals(expectedUserId)
                || totalAmount == null || totalAmount != expectedAmount) {
            throw new BusinessException(ErrorCode.EXTERNAL_PAYMENT_ERROR,
                    "카카오페이 승인 결과가 결제 준비 정보와 일치하지 않습니다.");
        }
    }

    public void markApproved(String aid, long vatAmount, String paymentMethodType,
                             LocalDateTime consumedAt) {
        this.aid = aid;
        this.vatAmount = vatAmount;
        this.paymentMethodType = paymentMethodType;
        this.stateConsumedAt = consumedAt;
    }

    public boolean hasReusableReady(LocalDateTime now) {
        return tid != null && nextRedirectPcUrl != null && stateNonce != null
                && stateConsumedAt == null && stateExpiresAt != null
                && now.isBefore(stateExpiresAt);
    }

    public boolean isApprovedWithNonce(String nonce) {
        return aid != null && stateConsumedAt != null && stateNonce != null
                && stateNonce.equals(nonce);
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getTid() {
        return tid;
    }

    public String getPartnerOrderId() {
        return partnerOrderId;
    }

    public String getPartnerUserId() {
        return partnerUserId;
    }

    public Long getTotalAmount() {
        return totalAmount;
    }

    public String getNextRedirectPcUrl() {
        return nextRedirectPcUrl;
    }

    public String getStateNonce() {
        return stateNonce;
    }

    public LocalDateTime getStateExpiresAt() {
        return stateExpiresAt;
    }

    public LocalDateTime getStateConsumedAt() {
        return stateConsumedAt;
    }

    public String getAid() {
        return aid;
    }
}
