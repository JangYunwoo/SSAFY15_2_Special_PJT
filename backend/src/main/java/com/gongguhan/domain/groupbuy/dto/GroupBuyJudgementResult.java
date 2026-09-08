package com.gongguhan.domain.groupbuy.dto;

public record GroupBuyJudgementResult(
        int processedCount,
        int fulfilledCount,
        int failedCount
) {
}
