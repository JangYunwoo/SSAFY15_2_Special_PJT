package com.gongguhan.domain.groupbuy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SellerGroupBuySummaryResponse(
        Long id,
        Long productId,
        String productName,
        String status,
        int targetCount,
        int paidCount,
        int confirmedCount,
        long productPrice,
        long unitShareAmount,
        long shippingFee,
        OffsetDateTime recruitEndAt,
        BigDecimal invoiceRegisteredRate
) {
}
