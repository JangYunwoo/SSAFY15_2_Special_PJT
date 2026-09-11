package com.gongguhan.domain.returnrequest.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ReturnDetailResponse(
        Long id,
        Long participationId,
        Long groupBuyId,
        String productName,
        String buyerName,
        String reasonType,
        String detailReason,
        String status,
        long refundAmount,
        long returnShippingFee,
        String shippingFeeBearer,
        OffsetDateTime requestedAt,
        OffsetDateTime autoApproveAt,
        boolean postConfirm,
        boolean adminReviewRequired,
        String pickupZipcode,
        String pickupAddress1,
        String pickupAddress2,
        String rejectReason,
        List<ReturnHistoryResponse> history
) {
    public static ReturnDetailResponse from(ReturnSummaryResponse summary,
                                            String pickupZipcode,
                                            String pickupAddress1,
                                            String pickupAddress2,
                                            String rejectReason,
                                            List<ReturnHistoryResponse> history) {
        return new ReturnDetailResponse(
                summary.id(), summary.participationId(), summary.groupBuyId(),
                summary.productName(), summary.buyerName(), summary.reasonType(),
                summary.detailReason(), summary.status(), summary.refundAmount(),
                summary.returnShippingFee(), summary.shippingFeeBearer(),
                summary.requestedAt(), summary.autoApproveAt(), summary.postConfirm(),
                summary.adminReviewRequired(), pickupZipcode, pickupAddress1,
                pickupAddress2, rejectReason, history);
    }
}
