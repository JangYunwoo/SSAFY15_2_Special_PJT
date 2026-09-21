package com.gongguhan.domain.recommendation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record RecommendationEventRequest(
        @NotBlank @Size(max = 36) String sessionId,
        @NotEmpty @Size(max = 100) List<@Valid RecommendationEventItem> events
) {
}
