package com.gongguhan.domain.groupbuy.dto;

import java.time.OffsetDateTime;

public record SellerParticipantResponse(
        String participationId,
        String buyerName,
        String recipientName,
        String zipcode,
        String address1,
        String address2,
        OffsetDateTime paidAt,
        long amount,
        String status
) {
}
