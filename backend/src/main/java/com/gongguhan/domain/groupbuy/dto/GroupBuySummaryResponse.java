package com.gongguhan.domain.groupbuy.dto;

import java.time.OffsetDateTime;

public record GroupBuySummaryResponse(
        Long id,
        Long productId,
        String productName,
        String thumbnailUrl,
        String sellerName,
        Long categoryId,
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
