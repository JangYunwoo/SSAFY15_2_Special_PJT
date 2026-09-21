package com.gongguhan.domain.recommendation.dto;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(
        List<RecommendedGroupBuyResponse> items,
        String modelType,
        Map<String, Double> featureWeights
) {
}
