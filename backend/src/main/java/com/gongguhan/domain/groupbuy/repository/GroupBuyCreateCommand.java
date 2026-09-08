package com.gongguhan.domain.groupbuy.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GroupBuyCreateCommand(
        Long productId,
        Long sellerId,
        long productPrice,
        int targetParticipants,
        long unitShareAmount,
        long shippingFee,
        long roundingDiff,
        BigDecimal platformFeeRate,
        long expectedPlatformFee,
        long expectedSettlementAmount,
        LocalDateTime recruitStartAt,
        LocalDateTime recruitEndAt
) {
}
