package com.gongguhan.domain.refund.entity;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 환불 원장 (RFD-01~04, FR-RFD-01~10).
 *
 * <p><b>이 행 하나가 "구매자에게 돈을 돌려주기로 했다" 는 결정입니다.</b> 행이 생기는
 * 시점과 실제로 돈이 나가는 시점이 다르기 때문에 상태를 따로 둡니다. 외부 이체와
 * 온체인 송금은 실패할 수 있고, 실패해도 결정 자체는 남아 있어야 재처리할 수 있습니다.
 *
 * <p><b>행을 만드는 주체가 여러 도메인입니다.</b> 모집 실패는 공동구매, 참여 취소는
 * 참여, 반품은 반품 도메인, 미매칭 입금은 코인 결제가 만듭니다. 그래서 각 도메인이
 * 이 엔티티를 직접 다루지 않도록
 * {@link com.gongguhan.domain.refund.service.RefundLedgerService} 를 경계로 둡니다.
 *
 * <p><b>금액은 항상 원화입니다.</b> 코인으로 결제한 건도 에스크로에 원화로 보관되므로
 * (FR-STL-10) 환불 기준 금액도 원화입니다. 온체인으로 돌려줄 때의 토큰 수량은
 * {@code assetAmount} 에 별도로 남깁니다. 그 시점 환율로 환산한 값이라 원화 금액과
 * 1:1 로 대응하지 않습니다.
 *
 * <p><b>이중 환불은 DB 가 막습니다.</b> {@code refundMarker} 가 활성 상태에서만 Y 이고
 * {@code UNIQUE(participation_id, refund_marker)} 가 걸려 있어, 배치를 두 번 돌려도
 * 같은 참여 건에 두 번째 환불이 INSERT 되지 않습니다. 애플리케이션에서 조회로 막는
 * 방식은 두 요청이 동시에 조회를 통과할 수 있어 쓰지 않습니다.
 */
@Entity
@Table(name = "refunds", uniqueConstraints = {
        // V11 이 MySQL 에 만드는 제약과 같은 것을 엔티티에도 선언한다.
        // 테스트는 Flyway 가 아니라 엔티티에서 H2 스키마를 만들기 때문에, 여기 없으면
        // "이중 환불이 DB 에서 막힌다" 를 테스트로 확인할 수 없다.
        // ddl-auto=validate 는 인덱스·제약을 검사하지 않으므로 운영 스키마와 충돌하지 않는다.
        @UniqueConstraint(name = "uk_refunds_active_participation",
                columnNames = {"participation_id", "refund_marker"}),
        @UniqueConstraint(name = "uk_refunds_active_deposit",
                columnNames = {"deposit_id", "refund_marker"}),
        @UniqueConstraint(name = "uk_refunds_tx", columnNames = {"tx_hash"})
})
public class Refund {

    /** 활성 환불 표식. UNIQUE 에서 NULL 은 서로 충돌하지 않는다는 성질을 쓴다. */
    private static final String ACTIVE_MARKER = "Y";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "refund_id")
    private Long refundId;

    /**
     * 환불 대상 참여 건.
     *
     * <p>슬롯 만료 입금·미매칭 입금은 참여 건이 만들어지지 않으므로 NULL 입니다.
     * 돈은 들어왔지만 자리를 얻지 못한 상태입니다.
     */
    @Column(name = "participation_id")
    private Long participationId;

    /** 반품에서 시작된 환불만 채워집니다. 환불액 계산 근거를 되짚을 때 씁니다. */
    @Column(name = "return_id")
    private Long returnId;

    /** 미매칭 입금 환불에서 "어느 입금을 돌려보내는지" 를 가리킵니다. */
    @Column(name = "deposit_id")
    private Long depositId;

    /** KRW 기준. */
    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_type", nullable = false, length = 30)
    private RefundReason reasonType;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_method", nullable = false, length = 20)
    private RefundMethod refundMethod;

    /** SSAFY 금융망 요청 기관거래고유번호. 카카오페이는 취소 aid 를 담습니다. */
    @Column(name = "institution_tx_no", length = 20)
    private String institutionTxNo;

    @Column(name = "ssafy_transaction_no")
    private Long ssafyTransactionNo;

    /** 계좌이체 환불 대상 계좌. */
    @Column(name = "to_account_no", length = 16)
    private String toAccountNo;

    /** 코인 환불 수취 주소. 구매자가 결제 때 송금한 지갑입니다. */
    @Column(name = "to_address", length = 42)
    private String toAddress;

    /** 코인 환불 토큰 수량. 사람이 읽는 단위입니다. */
    @Column(name = "asset_amount", precision = 36, scale = 18)
    private BigDecimal assetAmount;

    /** 코인 환불 트랜잭션 해시. UNIQUE 라 같은 송금을 두 번 기록할 수 없습니다. */
    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundStatus status;

    @Column(name = "refund_marker", length = 1)
    private String refundMarker;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "execution_claimed", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean executionClaimed;

    public void claimExecution(String correlationId) {
        requireProcessing();
        if (executionClaimed) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "외부 환불 실행 결과 대조가 필요합니다.");
        }
        executionClaimed = true;
        if (correlationId != null) { institutionTxNo = correlationId; }
    }

    public void releaseUnsentOrRejectedExecution() {
        requireProcessing();
        executionClaimed = false;
    }

    public void recordInstitutionTransaction(String correlationId) {
        requireProcessing();
        if (!executionClaimed) { throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION); }
        institutionTxNo = correlationId;
    }

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    protected Refund() {
    }

    private Refund(Long participationId, Long returnId, Long depositId, long amount,
                   RefundReason reasonType, RefundMethod refundMethod,
                   String toAddress, LocalDateTime createdAt) {
        if (amount < 0 || (amount == 0 && reasonType != RefundReason.RETURN)) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "환불 금액은 양수여야 하며 배송비 공제 반품만 0원을 허용합니다. amount=" + amount);
        }
        this.participationId = participationId;
        this.returnId = returnId;
        this.depositId = depositId;
        this.amount = amount;
        this.reasonType = reasonType;
        this.refundMethod = refundMethod;
        this.toAddress = toAddress;
        this.status = RefundStatus.PROCESSING;
        this.refundMarker = ACTIVE_MARKER;
        this.createdAt = createdAt;
    }

    /**
     * 참여 건에 대한 환불을 개시합니다.
     *
     * <p>모집 실패·참여 취소·판매자 귀책이 이 경로입니다. 반품은 {@link #forReturn} 을
     * 쓰세요 — 반품 ID 를 남겨야 환불액 계산 근거를 되짚을 수 있습니다.
     *
     * @param createdAt {@code Clock} 빈에서 가져온 값을 넘깁니다. 엔티티가 직접
     *                  {@code LocalDateTime.now()} 를 부르면 테스트에서 시각을 고정할 수
     *                  없습니다.
     */
    public static Refund forParticipation(Long participationId, long amount,
                                          RefundReason reasonType, RefundMethod refundMethod,
                                          LocalDateTime createdAt) {
        if (reasonType == RefundReason.RETURN) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "반품 환불은 forReturn 으로 만들어야 합니다. participationId="
                            + participationId);
        }
        return new Refund(participationId, null, null, amount, reasonType, refundMethod,
                null, createdAt);
    }

    /** 반품 승인·수거 완료에 따른 환불 (FR-RTN-17). */
    public static Refund forReturn(Long returnId, Long participationId, long amount,
                                   RefundMethod refundMethod, LocalDateTime createdAt) {
        return new Refund(participationId, returnId, null, amount, RefundReason.RETURN,
                refundMethod, null, createdAt);
    }

    /**
     * 결제에 붙이지 못한 입금을 돌려보냅니다 (FR-PAY-26).
     *
     * <p>참여 건이 없으므로 {@code participationId} 가 NULL 입니다. 보낸 지갑으로
     * 그대로 되돌리는 것이 원칙이라 {@code toAddress} 를 개시 시점에 받습니다.
     * 송금 결과를 기록할 때가 아니라 <b>보내기 전에</b> 목적지를 남겨야, 송금 직후
     * 장애가 나도 어디로 보냈는지 추적할 수 있습니다.
     */
    public static Refund forUnmatchedDeposit(Long depositId, long amount, String toAddress,
                                             LocalDateTime createdAt) {
        return forDeposit(depositId, amount, toAddress, RefundReason.UNMATCHED_DEPOSIT,
                createdAt);
    }

    /**
     * 슬롯이 만료된 뒤 도착한 입금을 돌려보냅니다 (FR-SLT-10).
     *
     * <p>자리는 다른 사람에게 넘어갔고 돈만 우리에게 있는 상태입니다.
     */
    public static Refund forExpiredSlot(Long depositId, long amount, String toAddress,
                                        LocalDateTime createdAt) {
        return forDeposit(depositId, amount, toAddress, RefundReason.SLOT_EXPIRED, createdAt);
    }

    private static Refund forDeposit(Long depositId, long amount, String toAddress,
                                     RefundReason reasonType, LocalDateTime createdAt) {
        if (depositId == null) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "입금 환불에는 입금 ID 가 필요합니다. reason=" + reasonType);
        }
        if (toAddress == null || toAddress.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "코인 환불 수취 주소가 없습니다. depositId=" + depositId);
        }
        return new Refund(null, null, depositId, amount, reasonType, RefundMethod.CRYPTO,
                toAddress, createdAt);
    }

    /**
     * 코인 환불 수취 주소를 정합니다.
     *
     * <p>참여 건 환불은 개시 시점에 주소를 모릅니다 — 어느 결제였는지는 알지만 그
     * 결제에 쓰인 지갑은 {@code crypto_payments} 를 봐야 알 수 있습니다. <b>송금 직전이
     * 아니라 송금 전에</b> 기록해야, 송금 후 장애가 나도 어디로 보냈는지 남습니다.
     */
    public void assignWalletAddress(String address) {
        requireProcessing();
        requireMethod(RefundMethod.CRYPTO);
        if (address == null || address.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "코인 환불 수취 주소가 없습니다. refundId=" + refundId);
        }
        this.toAddress = address;
    }

    /** 계좌이체 환불 대상 계좌를 정합니다. 카드 취소 불가로 폴백할 때 씁니다. */
    public void assignAccount(String accountNo) {
        requireProcessing();
        requireMethod(RefundMethod.ACCOUNT_TRANSFER);
        if (accountNo == null || accountNo.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "환불 계좌번호가 없습니다. refundId=" + refundId);
        }
        this.toAccountNo = accountNo;
    }

    /**
     * 카드 취소가 불가해 계좌이체 환불로 전환합니다.
     *
     * <p>SSAFY 카드 취소는 전액·청구서 발행 전에만 가능합니다. 부분 환불이거나 청구서가
     * 발행된 건은 {@code CardRefundPort} 가 {@code REQUIRES_TRANSFER_FALLBACK} 로 알리고,
     * 호출자가 이 메서드로 수단을 바꾼 뒤 계좌이체 경로를 탑니다. 아직 진행 중일 때만
     * 바꿉니다 — 완료·실패한 건의 수단을 바꾸면 원장 이력이 어긋납니다.
     */
    public void fallbackToAccountTransfer() {
        requireProcessing();
        requireMethod(RefundMethod.CARD_CANCEL);
        this.refundMethod = RefundMethod.ACCOUNT_TRANSFER;
    }

    /**
     * 카드 승인 취소가 확정됐습니다.
     *
     * <p>SSAFY 카드 취소는 거래 단위 전액 취소입니다. 부분 환불은 이 경로로 올 수
     * 없고 계좌이체로 전환돼야 합니다.
     */
    public void completeByCardCancel(Long ssafyTransactionNo, LocalDateTime processedAt) {
        requireMethod(RefundMethod.CARD_CANCEL);
        this.ssafyTransactionNo = ssafyTransactionNo;
        markCompleted(processedAt);
    }

    /** 배송비 공제 후 환불액이 0원인 반품은 외부 거래를 만들지 않는다. */
    public void completeWithoutTransfer(LocalDateTime processedAt) {
        if (amount != 0 || reasonType != RefundReason.RETURN) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
        markCompleted(processedAt);
    }

    /** 계좌 이체가 확정됐습니다. */
    public void completeByAccountTransfer(String institutionTxNo, Long ssafyTransactionNo,
                                          LocalDateTime processedAt) {
        requireMethod(RefundMethod.ACCOUNT_TRANSFER);
        this.institutionTxNo = institutionTxNo;
        this.ssafyTransactionNo = ssafyTransactionNo;
        markCompleted(processedAt);
    }

    /** 카카오페이 결제 취소가 확정됐습니다. {@code cancelAid} 는 제공사 취소 식별자입니다. */
    public void completeByKakaoPay(String cancelAid, LocalDateTime processedAt) {
        requireMethod(RefundMethod.KAKAOPAY);
        this.institutionTxNo = cancelAid;
        markCompleted(processedAt);
    }

    /**
     * 온체인 송금이 확정됐습니다.
     *
     * <p>{@code txHash} 가 UNIQUE 이므로 같은 송금을 두 건의 환불로 기록하려 하면 DB 가
     * 거부합니다. 송금은 되돌릴 수 없어서 기록 단계에서 막아야 합니다.
     */
    public void completeByCrypto(String txHash, BigDecimal assetAmount,
                                 LocalDateTime processedAt) {
        requireMethod(RefundMethod.CRYPTO);
        if (txHash == null || txHash.isBlank()) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "코인 환불 완료에는 트랜잭션 해시가 필요합니다. refundId=" + refundId);
        }
        this.txHash = txHash;
        this.assetAmount = assetAmount;
        markCompleted(processedAt);
    }

    /**
     * 환불이 실패했습니다. 재처리 대상으로 남습니다 (FR-RFD-09).
     *
     * <p>마커를 비워 유일성 검사에서 제외합니다. 그래야 같은 참여 건에 대해 새
     * 환불을 만들거나 이 행을 재시도할 수 있습니다.
     */
    public void markFailed(String reason, LocalDateTime processedAt) {
        requireProcessing();
        this.status = RefundStatus.FAILED;
        this.refundMarker = executionClaimed ? ACTIVE_MARKER : null;
        this.failReason = reason;
        this.processedAt = processedAt;
    }

    /**
     * 관리자 재처리로 다시 진행 상태로 되돌립니다 (RFD-04, FR-ADM-08).
     *
     * <p>완료된 건은 돌아올 수 없습니다. 이미 돈이 나갔으므로 다시 보내면 이중
     * 지급입니다.
     *
     * <p>마커를 다시 채우므로, 그 사이 같은 참여 건에 다른 환불이 생겼다면 DB 가
     * 거부합니다. 조용히 두 건이 진행되는 것보다 낫습니다.
     */
    public void retry() {
        if (executionClaimed) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "이전 환불의 외부 결과를 대조하기 전에는 재실행할 수 없습니다.");
        }
        if (status != RefundStatus.FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "실패한 환불만 재처리할 수 있습니다. refundId=" + refundId
                            + " status=" + status);
        }
        this.status = RefundStatus.PROCESSING;
        this.refundMarker = ACTIVE_MARKER;
        this.failReason = null;
        this.processedAt = null;
    }

    private void markCompleted(LocalDateTime processedAt) {
        requireProcessing();
        this.status = RefundStatus.COMPLETED;
        this.processedAt = processedAt;
    }

    private void requireMethod(RefundMethod expected) {
        if (refundMethod != expected) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "환불 수단이 다릅니다. refundId=" + refundId + " expected=" + expected
                            + " actual=" + refundMethod);
        }
    }

    /**
     * 진행 중이 아니면 거부합니다.
     *
     * <p>이중 완료를 막는 지점입니다. 재처리 배치를 다시 돌려도 이미 완료된 건이 두 번
     * 완료되지 않아야 합니다(NFR-OP-03).
     */
    private void requireProcessing() {
        if (status != RefundStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "진행 중인 환불이 아닙니다. refundId=" + refundId + " status=" + status);
        }
    }

    public boolean isProcessing() {
        return status == RefundStatus.PROCESSING;
    }

    public boolean isExecutionClaimed() { return executionClaimed; }

    public boolean isCompleted() {
        return status == RefundStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == RefundStatus.FAILED;
    }

    public Long getRefundId() {
        return refundId;
    }

    public Long getParticipationId() {
        return participationId;
    }

    public Long getReturnId() {
        return returnId;
    }

    public Long getDepositId() {
        return depositId;
    }

    public long getAmount() {
        return amount;
    }

    public RefundReason getReasonType() {
        return reasonType;
    }

    public RefundMethod getRefundMethod() {
        return refundMethod;
    }

    public String getInstitutionTxNo() {
        return institutionTxNo;
    }

    public Long getSsafyTransactionNo() {
        return ssafyTransactionNo;
    }

    public String getToAccountNo() {
        return toAccountNo;
    }

    public String getToAddress() {
        return toAddress;
    }

    public BigDecimal getAssetAmount() {
        return assetAmount;
    }

    public String getTxHash() {
        return txHash;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public String getFailReason() {
        return failReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
