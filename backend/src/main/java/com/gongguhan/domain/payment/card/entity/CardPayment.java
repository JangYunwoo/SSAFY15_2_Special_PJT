package com.gongguhan.domain.payment.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * SSAFY 카드 결제 상세. {@code payments}와 1:1이며 PK를 그대로 공유한다({@code payment_id}).
 * 이 코드베이스는 도메인 간 JPA 연관관계 대신 평범한 FK 컬럼을 쓰므로({@link
 * com.gongguhan.domain.payment.common.entity.Payment#getGroupBuyId()} 참고) 여기도
 * {@code @OneToOne}을 걸지 않는다.
 *
 * <p>카드번호·CVC는 이 엔티티에도, 다른 어디에도 저장하지 않는다 - {@link #cardNoMasked}만
 * 남긴다.
 */
@Entity
@Table(name = "card_payments")
public class CardPayment {

    public static final String CANCEL_STATUS_SUCCESS = "SUCCESS";
    public static final String CANCEL_STATUS_FAIL = "FAIL";

    @Id
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "ssafy_user_key", length = 60)
    private String ssafyUserKey;

    @Column(name = "card_no_masked", length = 19)
    private String cardNoMasked;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_name", length = 100)
    private String merchantName;

    @Column(name = "category_id", length = 20)
    private String categoryId;

    @Column(name = "institution_tx_no", length = 20)
    private String institutionTxNo;

    @Column(name = "transaction_unique_no")
    private Long transactionUniqueNo;

    @Column(name = "transaction_date", length = 8)
    private String transactionDate;

    @Column(name = "transaction_time", length = 6)
    private String transactionTime;

    @Column(name = "payment_balance")
    private Long paymentBalance;

    @Column(name = "billing_issued", nullable = false)
    private boolean billingIssued = false;

    @Column(name = "billing_issued_at")
    private LocalDateTime billingIssuedAt;

    @Column(name = "cancel_institution_tx_no", length = 20)
    private String cancelInstitutionTxNo;

    @Column(name = "cancel_status", length = 10)
    private String cancelStatus;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "raw_response", columnDefinition = "json")
    private String rawResponse;

    protected CardPayment() {
    }

    private CardPayment(Long paymentId, String ssafyUserKey, String cardNoMasked, Long merchantId,
                         String merchantName, String categoryId, String institutionTxNo,
                         Long transactionUniqueNo, String transactionDate, String transactionTime,
                         Long paymentBalance, String rawResponse) {
        this.paymentId = paymentId;
        this.ssafyUserKey = ssafyUserKey;
        this.cardNoMasked = cardNoMasked;
        this.merchantId = merchantId;
        this.merchantName = merchantName;
        this.categoryId = categoryId;
        this.institutionTxNo = institutionTxNo;
        this.transactionUniqueNo = transactionUniqueNo;
        this.transactionDate = transactionDate;
        this.transactionTime = transactionTime;
        this.paymentBalance = paymentBalance;
        this.rawResponse = rawResponse;
    }

    public static CardPayment create(Long paymentId, String ssafyUserKey, String cardNoMasked, Long merchantId,
                                      String merchantName, String categoryId, String institutionTxNo,
                                      Long transactionUniqueNo, String transactionDate, String transactionTime,
                                      Long paymentBalance, String rawResponse) {
        return new CardPayment(paymentId, ssafyUserKey, cardNoMasked, merchantId, merchantName, categoryId,
                institutionTxNo, transactionUniqueNo, transactionDate, transactionTime, paymentBalance,
                rawResponse);
    }

    /** 마지막 4자리만 남기고 나머지는 {@code *}로 가린다. */
    public static String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() <= 4) {
            return "*".repeat(cardNumber == null ? 0 : cardNumber.length());
        }
        int visibleFrom = cardNumber.length() - 4;
        return "*".repeat(visibleFrom) + cardNumber.substring(visibleFrom);
    }

    /** 전액 취소 성공 기록. 멱등 재요청 판정({@link #isCancelSucceeded})의 근거가 된다. */
    public void markCanceled(String cancelInstitutionTxNo, LocalDateTime canceledAt) {
        this.cancelInstitutionTxNo = cancelInstitutionTxNo;
        this.cancelStatus = CANCEL_STATUS_SUCCESS;
        this.canceledAt = canceledAt;
    }

    /** 취소 실패 기록. 실패 상태는 재시도를 막지 않는다 - 성공만 멱등 처리 대상이다. */
    public void markCancelFailed(String cancelInstitutionTxNo) {
        this.cancelInstitutionTxNo = cancelInstitutionTxNo;
        this.cancelStatus = CANCEL_STATUS_FAIL;
    }

    /** A1058(청구서 발행 거래) 응답으로 확인된 사실을 기록한다 - 이후 카드 취소를 다시 시도하지 않는다. */
    public void markBillingIssued(LocalDateTime billingIssuedAt) {
        this.billingIssued = true;
        this.billingIssuedAt = billingIssuedAt;
    }

    public boolean isCancelSucceeded() {
        return CANCEL_STATUS_SUCCESS.equals(cancelStatus);
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public String getSsafyUserKey() {
        return ssafyUserKey;
    }

    public String getCardNoMasked() {
        return cardNoMasked;
    }

    public Long getTransactionUniqueNo() {
        return transactionUniqueNo;
    }

    public String getTransactionDate() {
        return transactionDate;
    }

    public Long getPaymentBalance() {
        return paymentBalance;
    }

    public boolean isBillingIssued() {
        return billingIssued;
    }

    public String getCancelStatus() {
        return cancelStatus;
    }

    public String getCancelInstitutionTxNo() {
        return cancelInstitutionTxNo;
    }

    public LocalDateTime getCanceledAt() {
        return canceledAt;
    }
}
