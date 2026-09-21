package com.gongguhan.domain.recommendation.dto;

import com.gongguhan.domain.groupbuy.dto.GroupBuySummaryResponse;

public record RecommendedGroupBuyResponse(
        GroupBuySummaryResponse groupBuy,
        double score,
        String reason
) {
}
