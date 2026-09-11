package com.gongguhan.domain.groupbuy.event;

import java.time.LocalDateTime;

public record GroupBuyFailedEvent(
        Long groupBuyId,
        Long sellerId,
        int paidParticipantCount,
        LocalDateTime failedAt,
        String reason
) {
}
