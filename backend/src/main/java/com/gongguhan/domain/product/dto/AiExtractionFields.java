package com.gongguhan.domain.product.dto;

import java.util.List;

public record AiExtractionFields(
        AiField<String> name,
        /** AI가 상품명을 확정하지 못했을 때의 후보 목록(name이 null일 때 채워진다). 프론트가 하나를 고른다. */
        List<String> nameCandidates,
        AiField<String> brand,
        AiField<String> categoryId,
        AiField<String> modelName,
        AiField<String> color,
        AiField<Long> price,
        AiField<String> description) {

    public static AiExtractionFields empty() {
        return new AiExtractionFields(null, List.of(), null, null, null, null, null, null);
    }
}
