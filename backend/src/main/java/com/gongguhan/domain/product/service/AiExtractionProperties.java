package com.gongguhan.domain.product.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-extraction")
public record AiExtractionProperties(String baseUrl, String path, int timeoutMs, int maxImages) {
}
