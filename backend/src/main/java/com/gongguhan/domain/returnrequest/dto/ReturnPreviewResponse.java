package com.gongguhan.domain.returnrequest.dto;

import java.time.OffsetDateTime;

public record ReturnPreviewResponse(
        boolean eligible,
        String ineligibleReason,
        OffsetDateTime applicationDeadline,
        long paymentAmount,
        long refundableShareAmount,
        long refundableOriginalShippingFee,
        long returnShippingFee,
        long refundAmount,
        String shippingFeeBearer
) {
}
