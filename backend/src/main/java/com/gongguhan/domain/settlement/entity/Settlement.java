package com.gongguhan.domain.settlement.entity;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
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
 * 판매자 정산 (STL-01~07, FR-STL-01~14).
 *
 * <p><b>공동구매 한 건에 대한 한 회차의 지급 명세입니다.</b> 구매확정되고 정산 가능
 * 시점이 지난 참여 건들을 모아 한 번에 계산합니다.
 *
 * <h2>왜 회차({@code roundNo})가 있는가</h2>
 *
 * <p>같은 공동구매의 참여자들이 동시에 구매확정하지 않습니다. 배송 완료 시점이 다르고
 * ({@code settlement_eligible_at} = 배송완료 + 7일), 반품·분쟁으로 늦어지는 건도
 * 있습니다. 전원이 확정될 때까지 기다리면 판매자는 한 명 때문에 전체 대금을 못
 * 받습니다. 그래서 <b>정산 가능한 건들만 모아 여러 번에 나눠</b> 지급하고,
 * {@code UNIQUE(group_buy_id, round_no)} 로 회차를 구분합니다.
 *
 * <h2>금액 계산</h2>
 *
 * <pre>
 *   플랫폼 수수료 = 상품 분담금 합계 × 수수료율   (배송비 제외, 원 단위 버림)
 *   정산 금액     = 분담금 합계 + 배송비 합계 + 절상 차액 − 플랫폼 수수료
 * </pre>
 *
 * <p><b>배송비에는 수수료를 매기지 않습니다.</b> 배송비는 판매자가 실제로 택배사에
 * 내는 실비라, 거기서 수수료를 떼면 판매자가 손해를 봅니다.
 *
 * <p>{@code platformFeeRate} 를 행에 복사해 두는 이유는, 나중에 수수료율 정책이
 * 바뀌어도 <b>이미 지급한 정산의 계산 근거가 흔들리지 않게</b> 하기 위함입니다.
 */
@Entity
@Table(name = "settlements")
public class Settlement {

    /** 이체 전. 관리자가 실행(STL-04)하기를 기다린다. */
    public static final String STATUS_PENDING = "PENDING";
    /** 이체 완료. */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 분쟁 등으로 보류 (STL-05, FR-STL-13). 실행할 수 없다. */
    public static final String STATUS_HOLD = "HOLD";

    /** 이체 미시도. */
    public static final String TRANSFER_PENDING = "PENDING";
    /** 이체 성공. */
    public static final String TRANSFER_COMPLETED = "COMPLETED";
    /** 이체 실패. 재처리 대상 (STL-07). */
    public static final String TRANSFER_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settlement_id")
    private Long settlementId;

    @Column(name = "group_buy_id", nullable = false)
    private Long groupBuyId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    /** 같은 공동구매의 몇 번째 정산인지. {@code UNIQUE(group_buy_id, round_no)}. */
    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "confirmed_count", nullable = false)
    private int confirmedCount;

    /** 상품 분담금 합계. 수수료 기준 금액입니다. */
    @Column(name = "share_total", nullable = false)
    private long shareTotal;

    /** 배송비 합계. 수수료를 매기지 않습니다. */
    @Column(name = "shipping_total", nullable = false)
    private long shippingTotal;

    /** 분담금 절상으로 생긴 차액. 판매자에게 돌아갑니다. */
    @Column(name = "rounding_diff", nullable = false)
    private long roundingDiff;

    /** 계산 시점의 수수료율 스냅샷. 정책이 바뀌어도 이 정산의 근거는 유지됩니다. */
    @Column(name = "platform_fee_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal platformFeeRate;

    @Column(name = "platform_fee", nullable = false)
    private long platformFee;

    /** 판매자에게 실제로 지급할 금액. */
    @Column(name = "settlement_amount", nullable = false)
    private long settlementAmount;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "transfer_status", nullable = false, length = 20)
    private String transferStatus;

    @Column(name = "institution_tx_no", length = 20)
    private String institutionTxNo;

    @Column(name = "withdrawal_tx_no")
    private Long withdrawalTxNo;

    @Column(name = "deposit_tx_no")
    private Long depositTxNo;

    @Column(name = "transferred_at")
    private LocalDateTime transferredAt;

    @Column(name = "transfer_fail_reason", length = 255)
    private String transferFailReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Settlement() {
    }

    private Settlement(Long groupBuyId, Long sellerId, int roundNo, int confirmedCount,
                       long shareTotal, long shippingTotal, long roundingDiff,
                       BigDecimal platformFeeRate, LocalDateTime createdAt) {
        this.groupBuyId = groupBuyId;
        this.sellerId = sellerId;
        this.roundNo = roundNo;
        this.confirmedCount = confirmedCount;
        this.shareTotal = shareTotal;
        this.shippingTotal = shippingTotal;
        this.roundingDiff = roundingDiff;
        this.platformFeeRate = platformFeeRate;
        this.platformFee = calculateFee(shareTotal, platformFeeRate);
        this.settlementAmount = shareTotal + shippingTotal + roundingDiff - this.platformFee;
        this.status = STATUS_PENDING;
        this.transferStatus = TRANSFER_PENDING;
        this.createdAt = createdAt;
    }

    /**
     * 정산을 생성합니다.
     *
     * @param roundNo         회차. 같은 공동구매에 이미 있는 회차면 DB 가 거부합니다
     * @param roundingDiff    분담금 절상 차액. 공동구매 생성 시 확정된 값입니다
     * @param platformFeeRate 계산 시점의 수수료율. 행에 복사해 둡니다
     * @param createdAt       {@code Clock} 빈에서 가져온 값을 넘깁니다
     */
    public static Settlement create(Long groupBuyId, Long sellerId, int roundNo,
                                    int confirmedCount, long shareTotal, long shippingTotal,
                                    long roundingDiff, BigDecimal platformFeeRate,
                                    LocalDateTime createdAt) {
        if (confirmedCount <= 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "정산 대상이 없습니다. groupBuyId=" + groupBuyId);
        }
        return new Settlement(groupBuyId, sellerId, roundNo, confirmedCount,
                shareTotal, shippingTotal, roundingDiff, platformFeeRate, createdAt);
    }

    /**
     * 플랫폼 수수료를 계산합니다.
     *
     * <p><b>원 단위 버림입니다.</b> 올리면 판매자가 받을 돈이 줄어드는 쪽으로 오차가
     * 쌓입니다. 배송비는 기준 금액에서 빠집니다 — 판매자가 택배사에 내는 실비라
     * 거기서 수수료를 떼면 판매자가 손해를 봅니다.
     */
    private static long calculateFee(long shareTotal, BigDecimal rate) {
        return BigDecimal.valueOf(shareTotal)
                .multiply(rate)
                .setScale(0, RoundingMode.DOWN)
                .longValue();
    }

    /**
     * 분쟁 등으로 정산을 보류합니다 (STL-05, FR-STL-13).
     *
     * <p>이미 이체된 건은 보류할 수 없습니다 — 돈이 이미 나갔으므로 보류가 아니라
     * 회수 문제입니다.
     */
    public void hold(String reason) {
        requireStatus(STATUS_PENDING, "보류");
        this.status = STATUS_HOLD;
        this.transferFailReason = reason;
    }

    /** 보류를 해제합니다 (STL-06). 다시 실행 대기 상태가 됩니다. */
    public void releaseHold() {
        requireStatus(STATUS_HOLD, "보류 해제");
        this.status = STATUS_PENDING;
        this.transferFailReason = null;
    }

    /**
     * 이체가 확정됐습니다 (STL-04).
     *
     * <p>이미 완료된 건은 다시 완료시킬 수 없습니다. 재처리 배치를 두 번 돌려도 같은
     * 정산이 두 번 지급되지 않아야 합니다(NFR-OP-03).
     */
    public void completeTransfer(String institutionTxNo, Long withdrawalTxNo, Long depositTxNo,
                                 LocalDateTime transferredAt) {
        requireStatus(STATUS_PENDING, "이체 완료 처리");
        this.institutionTxNo = institutionTxNo;
        this.withdrawalTxNo = withdrawalTxNo;
        this.depositTxNo = depositTxNo;
        this.transferredAt = transferredAt;
        this.transferStatus = TRANSFER_COMPLETED;
        this.status = STATUS_COMPLETED;
        this.transferFailReason = null;
    }

    /**
     * 이체가 실패했습니다. 재처리 대상으로 남습니다 (STL-07).
     *
     * <p>상태는 {@code PENDING} 그대로 둡니다. 이체만 실패했을 뿐 정산 자체는 유효하고,
     * 재시도하면 같은 금액을 다시 보내면 됩니다.
     */
    public void failTransfer(String reason) {
        requireStatus(STATUS_PENDING, "이체 실패 처리");
        this.transferStatus = TRANSFER_FAILED;
        this.transferFailReason = reason;
    }

    /** 이체를 실행할 수 있는 상태인지. 보류·완료 건은 실행 대상이 아닙니다. */
    public boolean isExecutable() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    public boolean isHeld() {
        return STATUS_HOLD.equals(status);
    }

    private void requireStatus(String expected, String action) {
        if (!expected.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "%s할 수 없는 상태입니다. settlementId=%d status=%s"
                            .formatted(action, settlementId, status));
        }
    }

    public Long getSettlementId() {
        return settlementId;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public int getConfirmedCount() {
        return confirmedCount;
    }

    public long getShareTotal() {
        return shareTotal;
    }

    public long getShippingTotal() {
        return shippingTotal;
    }

    public long getRoundingDiff() {
        return roundingDiff;
    }

    public BigDecimal getPlatformFeeRate() {
        return platformFeeRate;
    }

    public long getPlatformFee() {
        return platformFee;
    }

    public long getSettlementAmount() {
        return settlementAmount;
    }

    public String getStatus() {
        return status;
    }

    public String getTransferStatus() {
        return transferStatus;
    }

    public String getInstitutionTxNo() {
        return institutionTxNo;
    }

    public Long getWithdrawalTxNo() {
        return withdrawalTxNo;
    }

    public Long getDepositTxNo() {
        return depositTxNo;
    }

    public LocalDateTime getTransferredAt() {
        return transferredAt;
    }

    public String getTransferFailReason() {
        return transferFailReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
