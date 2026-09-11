package com.gongguhan.domain.penalty.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

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
    public static final String STATUS_CANDIDATE = "CANDIDATE";

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
}
