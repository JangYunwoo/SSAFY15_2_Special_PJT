package com.gongguhan.domain.recommendation.repository;

import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;

public record RecommendationCandidate(
        long groupBuyId,
        long productId,
        String brand,
        Long categoryId,
        double recruitmentRate,
        double discountRate,
        double deadlineUrgency,
        GroupBuySummaryResponse summary
) {
}
