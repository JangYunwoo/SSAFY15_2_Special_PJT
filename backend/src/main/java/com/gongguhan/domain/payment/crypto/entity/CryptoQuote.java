package com.gongguhan.domain.payment.crypto.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 코인 결제 환율 견적 (CRYP-02, FR-PAY-21~25·28~30).
 *
 * <pre>
 *   applied_rate = market_rate × (1 + buffer_rate)   scale 8,  HALF_UP
 *   asset_amount = krw_amount ÷ applied_rate         scale 18, UP(올림)
 * </pre>
 *
 * <p><b>Buffer를 가산하는 이유.</b> 견적 발급부터 컨펌 완료까지 몇 분이 걸리고 그
 * 사이 시세가 움직인다. 적용 환율을 시세보다 높게 잡으면 구매자가 내는 자산 수량이
 * 늘어나므로, 변동 위험을 플랫폼이 아니라 구매자가 흡수한다.
 *
 * <p><b>자산 수량을 올림하는 이유.</b> 내림하면 환산 오차만큼 원화가 덜 들어오고,
 * 그 차액이 전부 플랫폼 손실로 남는다. 올림해도 구매자 부담 증가는 최소 단위 수준이다.
 *
 * <p><b>{@code used} 플래그.</b> 하나의 견적이 두 결제에 쓰이면 환율 고정 구간이 늘어나
 * 그만큼 플랫폼이 시세 위험을 더 진다. 실제 차단은 이 컬럼을 원자적으로 claim하는
 * 리포지토리 쿼리가 담당한다(CRYP-03 범위).
 */
@Entity
@Table(name = "crypto_quotes")
public class CryptoQuote {

    /** {@code asset_amount} 컬럼이 DECIMAL(36,18)이므로 그에 맞춘다. */
    private static final int ASSET_SCALE = 18;
    /** {@code applied_rate}·{@code market_rate} 컬럼이 DECIMAL(20,8)이므로 그에 맞춘다. */
    private static final int RATE_SCALE = 8;
    /** {@code buffer_rate} 컬럼이 DECIMAL(6,4)이므로 그에 맞춘다. */
    private static final int BUFFER_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quote_id")
    private Long quoteId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    /** 거래소 시세 (KRW / 자산 1개). */
    @Column(name = "market_rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal marketRate;

    @Column(name = "buffer_rate", nullable = false, precision = 6, scale = 4)
    private BigDecimal bufferRate;

    @Column(name = "applied_rate", nullable = false, precision = 20, scale = 8)
    private BigDecimal appliedRate;

    @Column(name = "krw_amount", nullable = false)
    private long krwAmount;

    @Column(name = "asset_amount", nullable = false, precision = 36, scale = 18)
    private BigDecimal assetAmount;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean used;

    protected CryptoQuote() {
    }

    private CryptoQuote(Long assetId, BigDecimal marketRate, BigDecimal bufferRate,
                         BigDecimal appliedRate, long krwAmount, BigDecimal assetAmount,
                         LocalDateTime issuedAt, LocalDateTime expiresAt) {
        this.assetId = assetId;
        this.marketRate = marketRate;
        this.bufferRate = bufferRate;
        this.appliedRate = appliedRate;
        this.krwAmount = krwAmount;
        this.assetAmount = assetAmount;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.used = false;
    }

    /**
     * 견적을 발급한다.
     *
     * @param expiresAt 만료 시각. 호출 측이 계산해서 넘긴다. 슬롯 만료를 넘기지 않아야
     *                  하므로(FR-PAY-24) 정책 TTL과 슬롯 만료 중 이른 쪽을 써야 한다.
     */
    public static CryptoQuote issue(Long assetId, BigDecimal marketRate, BigDecimal bufferRate,
                                     long krwAmount, LocalDateTime issuedAt,
                                     LocalDateTime expiresAt) {
        BigDecimal applied = marketRate
                .multiply(BigDecimal.ONE.add(bufferRate))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);

        BigDecimal amount = BigDecimal.valueOf(krwAmount)
                .divide(applied, ASSET_SCALE, RoundingMode.UP);

        // 컬럼 스케일로 미리 맞춘다. 그러지 않으면 발급 직후 응답("0.003")과
        // DB에서 다시 읽은 응답("0.0030")의 문자열이 달라져, 프론트가 두 값을
        // 비교할 때 같은 견적인데도 다르다고 판단한다.
        return new CryptoQuote(assetId,
                marketRate.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                bufferRate.setScale(BUFFER_SCALE, RoundingMode.HALF_UP),
                applied,
                krwAmount,
                amount.setScale(ASSET_SCALE, RoundingMode.UP),
                issuedAt, expiresAt);
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public long remainingSeconds(LocalDateTime now) {
        if (isExpired(now)) {
            return 0;
        }
        return java.time.Duration.between(now, expiresAt).toSeconds();
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public Long getAssetId() {
        return assetId;
    }

    public BigDecimal getMarketRate() {
        return marketRate;
    }

    public BigDecimal getBufferRate() {
        return bufferRate;
    }

    public BigDecimal getAppliedRate() {
        return appliedRate;
    }

    public long getKrwAmount() {
        return krwAmount;
    }

    public BigDecimal getAssetAmount() {
        return assetAmount;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public boolean isUsed() {
        return used;
    }
}
