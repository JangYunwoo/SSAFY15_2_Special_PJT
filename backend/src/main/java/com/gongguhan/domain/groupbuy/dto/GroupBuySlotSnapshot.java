package com.gongguhan.domain.groupbuy.dto;

import java.time.LocalDateTime;

public record GroupBuySlotSnapshot(
        Long groupBuyId,
        long unitShareAmount,
        long shippingFee,
        int targetParticipants,
        int currentParticipants,
        int reservedSlots,
        LocalDateTime recruitEndAt,
        String status
) {
}
