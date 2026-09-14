package com.gongguhan.domain.groupbuy.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** GRP-06 판매자 운영 상세. 화면이 운영 판단에 필요한 집계까지 한 번에 제공한다. */
public record SellerGroupBuyDetailResponse(
        String id,
        String productId,
        String productName,
        String status,
        int targetCount,
        int paidCount,
        int confirmedCount,
        long productPrice,
        long unitShareAmount,
        long shippingFee,
        OffsetDateTime recruitEndAt,
        BigDecimal invoiceRegisteredRate,
        OffsetDateTime recruitStartAt,
        long roundingSurplus,
        BigDecimal platformFeeRate,
        long escrowHeldAmount,
        long estimatedSettlementAmount,
        int returnCount,
        boolean canEdit,
        boolean canCancel
) {
}
