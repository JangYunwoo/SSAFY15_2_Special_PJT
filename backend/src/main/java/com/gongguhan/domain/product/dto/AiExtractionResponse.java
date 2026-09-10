package com.gongguhan.domain.product.dto;

import java.util.List;

public record AiExtractionResponse(String status, AiExtractionFields fields, List<String> warnings) {

    public static AiExtractionResponse failed(String warning) {
        return new AiExtractionResponse("FAILED", AiExtractionFields.empty(), List.of(warning));
    }
}
