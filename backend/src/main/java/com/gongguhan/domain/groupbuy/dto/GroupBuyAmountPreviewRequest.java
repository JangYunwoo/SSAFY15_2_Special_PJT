package com.gongguhan.domain.groupbuy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record GroupBuyAmountPreviewRequest(
        @NotNull Long productId,
        @Positive long productPrice,
        @Min(2) int targetCount,
        @PositiveOrZero long shippingFee
) {
}
