package com.gongguhan.domain.recommendation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecommendationSessionEndRequest(@NotBlank @Size(max = 36) String sessionId) {
}
