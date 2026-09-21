package com.gongguhan.domain.recommendation.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
        String baseUrl,
        String path,
        int timeoutMs,
        int maxCandidates,
        int trainingEventLimit,
        boolean seedEnabled
) {
}
