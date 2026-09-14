package com.gongguhan.domain.returnrequest.dto;

import java.time.OffsetDateTime;

public record ReturnSummaryResponse(
        String id,
        String participationId,
        String groupBuyId,
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
        boolean adminReviewRequired
) {
}
