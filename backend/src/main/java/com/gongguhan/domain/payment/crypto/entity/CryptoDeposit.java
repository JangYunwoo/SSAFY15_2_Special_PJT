package com.gongguhan.domain.payment.crypto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDateTime;

/**
 * 온체인 입금 감지 원본.
 *
 * <p>수취 지갑으로 들어온 ERC-20 Transfer 는 결제와 매칭되든 안 되든 <b>무조건</b>
 * 남긴다. 미매칭 입금을 기록해 두지 않으면 "돈은 들어왔는데 어디에도 내역이 없는"
 * 상황을 사후에 복원할 수 없다.
 *
 * <p><b>{@code UNIQUE(tx_hash, log_index)} 가 중복 처리를 DB 레벨에서 막는다.</b>
 * 영수증 검증(CRYP-03)과 이벤트 스캔이 같은 전송을 각각 집어도 두 번 인정되지 않는다.
 * 한 트랜잭션에 Transfer 가 여러 개 들어갈 수 있으므로 해시만으로는 유일하지 않다.
 */
@Entity
@Table(name = "crypto_deposits")
public class CryptoDeposit {

    /** 감지만 됨. 아직 매칭 전. */
    public static final String STATUS_DETECTED = "DETECTED";
    /** 결제 건과 금액까지 일치. */
    public static final String STATUS_MATCHED = "MATCHED";
    /** 대응하는 결제 건이 없음. 관리자 확인 후 환불 대상. */
    public static final String STATUS_UNMATCHED = "UNMATCHED";
    /** 견적보다 적게 보냄. 결제로 인정하지 않는다. */
    public static final String STATUS_UNDERPAID = "UNDERPAID";
    /** 견적보다 많이 보냄. 결제는 인정하되 초과분은 환불 대상. */
    public static final String STATUS_OVERPAID = "OVERPAID";
    public static final String STATUS_REFUNDED = "REFUNDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deposit_id")
    private Long depositId;

    /** 매칭된 결제 건. 미매칭 입금이면 NULL. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "chain_id", nullable = false)
    private long chainId;

    @Column(name = "tx_hash", nullable = false, length = 66)
    private String txHash;

    @Column(name = "log_index", nullable = false)
    private int logIndex;

    @Column(name = "block_number", nullable = false)
    private long blockNumber;

    @Column(name = "token_contract", nullable = false, length = 42)
    private String tokenContract;

    @Column(name = "from_address", nullable = false, length = 42)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 42)
    private String toAddress;

    @Column(name = "raw_amount", nullable = false, length = 78)
    private String rawAmount;

    @Column(name = "asset_amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal assetAmount;

    @Column(nullable = false)
    private int confirmations;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(length = 255)
    private String memo;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "matched_at")
    private LocalDateTime matchedAt;

    protected CryptoDeposit() {
    }

    private CryptoDeposit(long chainId, String txHash, int logIndex, long blockNumber,
                           String tokenContract, String fromAddress, String toAddress,
                           BigInteger rawAmount, BigDecimal assetAmount, int confirmations,
                           LocalDateTime detectedAt) {
        this.chainId = chainId;
        this.txHash = normalize(txHash);
        this.logIndex = logIndex;
        this.blockNumber = blockNumber;
        this.tokenContract = normalize(tokenContract);
        this.fromAddress = normalize(fromAddress);
        this.toAddress = normalize(toAddress);
        this.rawAmount = rawAmount.toString();
        this.assetAmount = assetAmount;
        this.confirmations = confirmations;
        this.status = STATUS_DETECTED;
        this.detectedAt = detectedAt;
    }

    public static CryptoDeposit detected(long chainId, String txHash, int logIndex,
                                          long blockNumber, String tokenContract,
                                          String fromAddress, String toAddress,
                                          BigInteger rawAmount, BigDecimal assetAmount,
                                          int confirmations, LocalDateTime detectedAt) {
        return new CryptoDeposit(chainId, txHash, logIndex, blockNumber, tokenContract,
                fromAddress, toAddress, rawAmount, assetAmount, confirmations, detectedAt);
    }

    public BigInteger rawAmountValue() {
        return new BigInteger(rawAmount);
    }

    /** 결제 건에 연결한다. 금액 판정 결과에 따라 MATCHED / UNDERPAID / OVERPAID. */
    public void matchTo(Long paymentId, String status, String memo, LocalDateTime now) {
        this.paymentId = paymentId;
        this.status = status;
        this.memo = memo;
        this.matchedAt = now;
    }

    public void markUnmatched(String memo, LocalDateTime now) {
        this.status = STATUS_UNMATCHED;
        this.memo = memo;
        this.matchedAt = now;
    }

    public void markRefunded() {
        this.status = STATUS_REFUNDED;
    }

    public void updateConfirmations(int confirmations) {
        this.confirmations = Math.max(0, confirmations);
    }

    private static String normalize(String value) {
        return value == null ? null : value.toLowerCase();
    }

    public Long getDepositId() {
        return depositId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public long getChainId() {
        return chainId;
    }

    public String getTxHash() {
        return txHash;
    }

    public int getLogIndex() {
        return logIndex;
    }

    public long getBlockNumber() {
        return blockNumber;
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

    public String getRawAmount() {
        return rawAmount;
    }

    public BigDecimal getAssetAmount() {
        return assetAmount;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public String getStatus() {
        return status;
    }

    public String getMemo() {
        return memo;
    }
}
