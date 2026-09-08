package com.gongguhan.domain.groupbuy.dto;

import java.time.OffsetDateTime;

public record BuyerParticipationSummaryResponse(
        Long id,
        Long groupBuyId,
        String productName,
        String thumbnailUrl,
        String sellerName,
        String status,
        String groupBuyStatus,
        String escrowStatus,
        String method,
        long shareAmount,
        long shippingFee,
        long totalAmount,
        OffsetDateTime paidAt,
        int targetCount,
        int paidCount,
        OffsetDateTime recruitEndAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime autoConfirmAt,
        OffsetDateTime withdrawalDeadlineAt,
        OffsetDateTime confirmedAt,
        boolean autoConfirmed
) {
}
