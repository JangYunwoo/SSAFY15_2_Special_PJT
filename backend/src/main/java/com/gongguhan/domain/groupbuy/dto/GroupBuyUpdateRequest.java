package com.gongguhan.domain.groupbuy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.OffsetDateTime;

/** GRP-07 부분 수정 요청. null인 필드는 기존 모집 조건을 유지한다. */
public record GroupBuyUpdateRequest(
        Long productId,
        @Positive Long productPrice,
        @Min(2) Integer targetCount,
        @PositiveOrZero Long shippingFee,
        OffsetDateTime recruitStartAt,
        OffsetDateTime recruitEndAt
) {
}
