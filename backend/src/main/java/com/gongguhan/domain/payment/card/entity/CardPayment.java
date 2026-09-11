package com.gongguhan.domain.payment.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    public Long getPaymentId() {
        return paymentId;
    }

    public String getCardNoMasked() {
        return cardNoMasked;
    }

    public Long getTransactionUniqueNo() {
        return transactionUniqueNo;
    }

    public boolean isBillingIssued() {
        return billingIssued;
    }
}
