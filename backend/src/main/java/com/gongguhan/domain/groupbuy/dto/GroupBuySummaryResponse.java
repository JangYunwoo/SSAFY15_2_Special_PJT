package com.gongguhan.domain.groupbuy.dto;

import java.time.OffsetDateTime;

public record GroupBuySummaryResponse(
        String id,
        String productId,
        String productName,
        String thumbnailUrl,
        String sellerName,
        String categoryId,
        String categoryName,
        String status,
        long listPrice,
        long productPrice,
        long unitShareAmount,
        long shippingFee,
        long personalPaymentAmount,
        int targetCount,
        int paidCount,
        double participationRate,
        OffsetDateTime recruitEndAt,
        OffsetDateTime createdAt
) {
}
