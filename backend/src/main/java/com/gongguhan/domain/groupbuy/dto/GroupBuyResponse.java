package com.gongguhan.domain.groupbuy.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public record GroupBuyResponse(
        Long groupBuyId,
        Long productId,
        Long sellerId,
        long productPrice,
        int targetCount,
        long unitShareAmount,
        long roundingSurplus,
        long shippingFee,
        BigDecimal platformFeeRate,
        long expectedPlatformFee,
        long expectedSettlementAmount,
        OffsetDateTime recruitStartAt,
        OffsetDateTime recruitEndAt,
        int currentParticipants,
        int reservedSlots,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
