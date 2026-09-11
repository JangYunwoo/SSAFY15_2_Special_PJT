package com.gongguhan.domain.groupbuy.event;

import java.time.LocalDateTime;

public record GroupBuyFulfilledEvent(
        Long groupBuyId,
        Long sellerId,
        int participantCount,
        long unitShareAmount,
        LocalDateTime fulfilledAt,
        LocalDateTime shippingDeadlineAt
) {
}
