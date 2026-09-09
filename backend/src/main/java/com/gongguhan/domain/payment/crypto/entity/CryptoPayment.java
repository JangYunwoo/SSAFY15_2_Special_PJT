package com.gongguhan.domain.payment.crypto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 코인 결제 상세 (CRYP-03, CRYP-04).
 *
 * <p>{@code payments} 와 PK 를 공유한다({@code payment_id}). 결제 공통 정보는 상위에
 * 두고 체인 관련 정보만 여기에 둔다.
 *
 * <p><b>주소·컨트랙트·필요 컨펌 수를 스냅샷으로 복사하는 이유.</b> 나중에
 * {@code crypto_assets} 의 필요 컨펌 수를 바꾸거나 수취 지갑을 교체해도, 진행 중인
 * 결제의 검증 기준이 흔들려서는 안 된다. 구매자가 송금한 시점의 조건으로 판정한다.
 *
 * <p><b>금액 비교는 {@code rawAmount} 문자열로만 한다.</b> 온체인 금액은 uint256 이라
 * {@code long} 범위를 넘을 수 있고, {@code BigDecimal} 로 변환하면 소수 자릿수 처리에서
 * 오차가 끼어들 여지가 생긴다. 사람이 읽는 {@code assetAmount} 는 표시 전용이다.
 */
@Entity
@Table(name = "crypto_payments")
public class CryptoPayment {

    public static final String RECEIPT_SUCCESS = "SUCCESS";
    public static final String RECEIPT_REVERTED = "REVERTED";

    /** {@code payments.payment_id} 를 그대로 쓴다. 별도 시퀀스가 없다. */
    @Id
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "quote_id", nullable = false)
    private Long quoteId;

    @Column(name = "wallet_id", nullable = false)
    private Long walletId;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "token_contract", nullable = false, length = 42)
    private String tokenContract;

    /** MetaMask 연결 계정. 구매자가 신고한 송신 주소. */
    @Column(name = "from_address", nullable = false, length = 42)
    private String fromAddress;

    /** 수취 주소 스냅샷. */
    @Column(name = "to_address", nullable = false, length = 42)
    private String toAddress;

    @Column(name = "asset_amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal assetAmount;

    /** uint256 원본값 문자열. 금액 대조는 이 값으로만 한다. */
    @Column(name = "raw_amount", nullable = false, length = 78)
    private String rawAmount;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(nullable = false)
    private int confirmations;

    @Column(name = "required_confirmations", nullable = false)
    private int requiredConfirmations;

    @Column(name = "receipt_status", length = 10)
    private String receiptStatus;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected CryptoPayment() {
    }

    private CryptoPayment(Long paymentId, CryptoAsset asset, CryptoQuote quote,
                           CryptoWallet wallet, String fromAddress, String txHash) {
        this.paymentId = paymentId;
        this.assetId = asset.getAssetId();
        this.quoteId = quote.getQuoteId();
        this.walletId = wallet.getWalletId();
        this.chainId = asset.getChainId();
        this.tokenContract = normalize(asset.getTokenContract());
        this.fromAddress = normalize(fromAddress);
        this.toAddress = normalize(wallet.getAddress());
        this.assetAmount = quote.getAssetAmount();
        this.rawAmount = asset.toRawAmount(quote.getAssetAmount()).toString();
        this.txHash = normalize(txHash);
        this.confirmations = 0;
        this.requiredConfirmations = asset.getRequiredConfirmations();
    }

    /**
     * 구매자가 제출한 트랜잭션으로 결제 상세를 만든다 (CRYP-03).
     *
     * <p>이 시점에는 아직 결제 완료가 아니다. 영수증 검증은 통과했지만 컨펌이
     * 부족하다. 완료 판정은 {@link #isConfirmed()} 가 true 가 된 뒤 스케줄러가 한다.
     */
    public static CryptoPayment submit(Long paymentId, CryptoAsset asset, CryptoQuote quote,
                                        CryptoWallet wallet, String fromAddress, String txHash) {
        return new CryptoPayment(paymentId, asset, quote, wallet, fromAddress, txHash);
    }

    public BigInteger expectedRawAmount() {
        return new BigInteger(rawAmount);
    }

    /** 영수증 조회 결과를 반영한다. */
    public void applyReceipt(boolean success, long blockNumber) {
        this.receiptStatus = success ? RECEIPT_SUCCESS : RECEIPT_REVERTED;
        this.blockNumber = blockNumber;
    }

    /**
     * 컨펌 수를 갱신한다.
     *
     * <p>최댓값을 유지하지 않고 그대로 반영한다. 체인 재구성(리오그)이 일어나면
     * 컨펌 수가 실제로 줄어드는데, 이전 최댓값을 붙들고 있으면 재구성으로 사라진
     * 트랜잭션을 결제 완료로 판정하게 된다.
     */
    public void updateConfirmations(int confirmations) {
        this.confirmations = Math.max(0, confirmations);
    }

    /** 결제 완료 조건: 영수증이 성공이고 필요 컨펌 수를 채웠다 (FR-PAY-26). */
    public boolean isConfirmed() {
        return RECEIPT_SUCCESS.equals(receiptStatus)
                && confirmations >= requiredConfirmations;
    }

    public boolean isReverted() {
        return RECEIPT_REVERTED.equals(receiptStatus);
    }

    public void markConfirmed(LocalDateTime now) {
        this.confirmedAt = now;
    }

    private static String normalize(String address) {
        // 온체인 주소는 EIP-55 체크섬 때문에 대소문자가 섞여 온다.
        // 저장·비교 기준을 소문자로 통일해야 대조가 어긋나지 않는다.
        return address == null ? null : address.toLowerCase();
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public Long getWalletId() {
        return walletId;
    }

    public long getChainId() {
        return chainId;
    }

    public String getTokenContract() {
        return tokenContract;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public BigDecimal getAssetAmount() {
        return assetAmount;
    }

    public String getRawAmount() {
        return rawAmount;
    }

    public String getTxHash() {
        return txHash;
    }

    public Long getBlockNumber() {
        return blockNumber;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public int getRequiredConfirmations() {
        return requiredConfirmations;
    }

    public String getReceiptStatus() {
        return receiptStatus;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }
}
