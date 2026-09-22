package com.gongguhan.domain.recommendation.repository;

import java.time.OffsetDateTime;

public record RecommendationTrainingEvent(
        String sessionId,
        long userId,
        long groupBuyId,
        long productId,
        String eventType,
        OffsetDateTime occurredAt,
        String brand,
        Long categoryId,
        double recruitmentRate,
        double discountRate,
        double deadlineUrgency
) {
}
