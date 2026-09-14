package com.gongguhan.domain.penalty.dto;

import com.gongguhan.domain.penalty.entity.SellerPenalty;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * PEN-01~06 위반 기록 응답 (API 명세서 13장).
 *
 * <p>{@code memo} 는 관리자의 판단 근거입니다. 판매자 본인 조회(PEN-01)에도 내보냅니다 —
 * 왜 확정됐는지 모르면 이의를 제기할 수 없고, 근거를 숨긴 제재는 분쟁을 키웁니다.
 *
 * <p>{@code reviewedBy} 는 관리자 ID 라 판매자에게는 의미가 없고 내부 식별자가 노출될
 * 뿐이므로 관리자 응답에서만 채웁니다.
 */
public record PenaltyResponse(
        String penaltyId,
        String sellerId,
        String groupBuyId,
        String reasonType,
        String detail,
        String status,
        String memo,
        String reviewedBy,
        String detectedAt,
        String reviewedAt
) {

    /** PEN-01 판매자 본인 조회. 관리자 식별자는 빼고 내보냅니다. */
    public static PenaltyResponse forSeller(SellerPenalty penalty) {
        return of(penalty, false);
    }

    /** PEN-02·03 관리자 조회. 누가 판단했는지 포함합니다. */
    public static PenaltyResponse forAdmin(SellerPenalty penalty) {
        return of(penalty, true);
    }

    private static PenaltyResponse of(SellerPenalty penalty, boolean includeReviewer) {
        return new PenaltyResponse(
                String.valueOf(penalty.getPenaltyId()),
                String.valueOf(penalty.getSellerId()),
                penalty.getGroupBuyId() == null ? null : String.valueOf(penalty.getGroupBuyId()),
                penalty.getReasonType(),
                penalty.getDetail(),
                penalty.getStatus(),
                penalty.getMemo(),
                includeReviewer && penalty.getReviewedBy() != null
                        ? String.valueOf(penalty.getReviewedBy()) : null,
                toIso(penalty.getDetectedAt()),
                toIso(penalty.getReviewedAt()));
    }

    private static String toIso(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC).toString();
    }
}
