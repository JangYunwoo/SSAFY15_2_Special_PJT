package com.gongguhan.domain.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record RecommendationEventItem(
        @NotNull @Positive Long groupBuyId,
        @NotBlank String eventType,
        @Size(max = 30) String source,
        @PositiveOrZero Integer position
) {
}
