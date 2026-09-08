package com.gongguhan.domain.groupbuy.dto;

import java.math.BigDecimal;

public record GroupBuyAmountPreviewResponse(
        long productPrice,
        int targetCount,
        long unitShareAmount,
        long roundingSurplus,
        long shippingFee,
        BigDecimal platformFeeRate,
        long estimatedPlatformFee,
        long estimatedSettlementAmount,
        String feePolicy
) {
}
