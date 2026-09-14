package com.gongguhan.domain.penalty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;

/**
 * 판매자 위반 기록 (영구 누적, 3회 확정 시 판매 정지).
 *
 * <p>S15P21B205-77 범위는 <b>후보(CANDIDATE) 등록</b>까지다 - 배송 기한 초과 배치와
 * 구매자 이의 제기가 여기로 후보를 쌓고, 관리자 검토 큐(status=CANDIDATE)에서
 * 확정(CONFIRMED)·기각(DISMISSED)하는 것은 제재 이슈(S15P21B205-83)의 몫이다.
 */
@Entity
@Table(name = "seller_penalties")
public class SellerPenalty {

    public static final String REASON_NO_INVOICE = "NO_INVOICE";
    public static final String REASON_FAKE_INVOICE = "FAKE_INVOICE";
    public static final String REASON_ITEM_MISMATCH = "ITEM_MISMATCH";
    public static final String REASON_WITHDRAWAL = "WITHDRAWAL";
    public static final String REASON_RETURN_REFUSAL = "RETURN_REFUSAL";
    public static final String REASON_RETURN_NEGLECT = "RETURN_NEGLECT";

    /** 관리자 검토 대기. {@code S15P21B205-77} 이 여기까지 만든다. */
    public static final String STATUS_CANDIDATE = "CANDIDATE";
    /** 위반으로 확정됨. 누적 횟수에 포함된다. */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** 위반이 아니라고 판단됨. 누적에 포함되지 않는다. */
    public static final String STATUS_DISMISSED = "DISMISSED";
    /** 확정을 되돌림 (FR-PEN-13). 누적에서 빠진다. */
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "penalty_id")
    private Long penaltyId;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "group_buy_id")
    private Long groupBuyId;

    @Column(name = "reason_type", nullable = false, length = 30)
    private String reasonType;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(length = 500)
    private String memo;

    protected SellerPenalty() {
    }

    private SellerPenalty(Long sellerId, Long groupBuyId, String reasonType, String detail,
                           LocalDateTime detectedAt) {
        this.sellerId = sellerId;
        this.groupBuyId = groupBuyId;
        this.reasonType = reasonType;
        this.detail = detail;
        this.status = STATUS_CANDIDATE;
        this.detectedAt = detectedAt;
    }

    public static SellerPenalty candidate(Long sellerId, Long groupBuyId, String reasonType,
                                           String detail, LocalDateTime detectedAt) {
        return new SellerPenalty(sellerId, groupBuyId, reasonType, detail, detectedAt);
    }

    /**
     * 위반으로 확정합니다 (PEN-04, FR-PEN-06~10).
     *
     * <p><b>확정만이 누적 횟수에 들어갑니다.</b> 후보는 배치와 구매자 이의 제기가
     * 자동으로 쌓는 것이라, 그대로 세면 사람 판단 없이 판매자가 정지됩니다.
     *
     * @param reviewedBy 판단한 관리자
     * @param memo       판단 근거. 나중에 판매자가 이의를 제기하면 이 기록이 근거가 됩니다
     */
    public void confirm(Long reviewedBy, String memo, LocalDateTime reviewedAt) {
        requireStatus(STATUS_CANDIDATE, "확정");
        this.status = STATUS_CONFIRMED;
        applyReview(reviewedBy, memo, reviewedAt);
    }

    /** 위반이 아니라고 판단해 기각합니다 (PEN-05, FR-PEN-06). */
    public void dismiss(Long reviewedBy, String memo, LocalDateTime reviewedAt) {
        requireStatus(STATUS_CANDIDATE, "기각");
        this.status = STATUS_DISMISSED;
        applyReview(reviewedBy, memo, reviewedAt);
    }

    /**
     * 확정을 되돌립니다 (PEN-06, FR-PEN-13).
     *
     * <p>잘못 확정한 건을 취소하는 경로입니다. 누적에서 빠지므로, 이 취소로 누적이
     * 임계 아래로 내려가면 판매 정지도 풀려야 합니다. 그 판단은 서비스가 합니다.
     *
     * <p>기각된 건은 애초에 누적에 없으므로 되돌릴 것이 없습니다.
     */
    public void revoke(Long reviewedBy, String memo, LocalDateTime reviewedAt) {
        requireStatus(STATUS_CONFIRMED, "취소");
        this.status = STATUS_REVOKED;
        applyReview(reviewedBy, memo, reviewedAt);
    }

    /** 누적 횟수에 포함되는 상태인지. */
    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equals(status);
    }

    /** 아직 관리자 판단을 기다리는 상태인지. */
    public boolean isCandidate() {
        return STATUS_CANDIDATE.equals(status);
    }

    private void applyReview(Long reviewedBy, String memo, LocalDateTime reviewedAt) {
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.memo = memo;
    }

    /**
     * 기대한 상태가 아니면 거부합니다.
     *
     * <p>이미 확정된 건을 또 확정하면 누적이 두 번 올라가 판매자가 부당하게 정지됩니다.
     * 관리자가 목록에서 같은 건을 두 번 누르는 것이 가장 흔한 경로입니다.
     */
    private void requireStatus(String expected, String action) {
        if (!expected.equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
                    "%s할 수 없는 상태입니다. penaltyId=%d status=%s"
                            .formatted(action, penaltyId, status));
        }
    }

    public Long getPenaltyId() {
        return penaltyId;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public Long getGroupBuyId() {
        return groupBuyId;
    }

    public String getReasonType() {
        return reasonType;
    }

    public String getDetail() {
        return detail;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getMemo() {
        return memo;
    }
}
