package com.gongguhan.domain.groupbuy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;

public record GroupBuyCreateRequest(
        @NotNull Long productId,
        @Positive long productPrice,
        @Min(2) int targetCount,
        @PositiveOrZero long shippingFee,
        @NotNull OffsetDateTime recruitStartAt,
        @NotNull OffsetDateTime recruitEndAt
) {
}
