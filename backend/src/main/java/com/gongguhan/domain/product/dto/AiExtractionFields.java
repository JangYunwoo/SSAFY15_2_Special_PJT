package com.gongguhan.domain.product.dto;

public record AiExtractionFields(
        AiField<String> name,
        AiField<String> brand,
        AiField<String> categoryId,
        AiField<String> modelName,
        AiField<String> color,
        AiField<Long> price,
        AiField<String> description) {

    public static AiExtractionFields empty() {
        return new AiExtractionFields(null, null, null, null, null, null, null);
    }
}
