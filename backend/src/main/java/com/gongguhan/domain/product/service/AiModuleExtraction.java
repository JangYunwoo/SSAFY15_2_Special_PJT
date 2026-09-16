package com.gongguhan.domain.product.service;

import com.gongguhan.domain.product.dto.AiField;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AiModuleExtraction(
        String status,
        Fields fields,
        @JsonProperty("result_json") ResultJson resultJson,
        @JsonProperty("confidence_json") ConfidenceJson confidenceJson,
        List<String> warnings,
        @JsonProperty("error_message") String errorMessage) {

    public AiModuleExtraction(String status, Fields fields, List<String> warnings) {
        this(status, fields, null, null, warnings, null);
    }

    public Fields normalizedFields() {
        if (fields != null) {
            return fields;
        }
        if (resultJson == null) {
            return null;
        }
        ConfidenceJson confidence = confidenceJson == null ? ConfidenceJson.empty() : confidenceJson;
        return new Fields(
                field(resultJson.name(), confidence.name()),
                field(resultJson.brand(), confidence.brand()),
                field(resultJson.categoryName(), confidence.category()),
                field(resultJson.modelName(), confidence.modelName()),
                field(resultJson.color(), confidence.color()),
                field(resultJson.price(), confidence.price()),
                field(resultJson.description(), confidence.description()));
    }

    private static <T> AiField<T> field(T value, BigDecimal confidence) {
        return value == null ? null : new AiField<>(value, confidence == null ? BigDecimal.ZERO : confidence);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Fields(
            AiField<String> name,
            AiField<String> brand,
            @JsonAlias({"categoryName", "category_name"})
            AiField<String> category,
            @JsonAlias("model_name")
            AiField<String> modelName,
            AiField<String> color,
            AiField<Long> price,
            AiField<String> description) {
    }

    /** AI가 상품명을 확정하지 못했을 때 주는 후보 목록(name은 null). 없으면 빈 목록. */
    public List<String> nameCandidates() {
        if (resultJson == null || resultJson.nameCandidates() == null) {
            return List.of();
        }
        return resultJson.nameCandidates();
    }

    /** AI가 카테고리 ID를 직접 준 경우(신규 계약). 없으면 null이라 category_name 매칭으로 폴백. */
    public Long directCategoryId() {
        return resultJson == null ? null : resultJson.categoryId();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultJson(
            String name,
            @JsonProperty("name_candidates") List<String> nameCandidates,
            String brand,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("category_id") Long categoryId,
            @JsonProperty("model_name") String modelName,
            String color,
            Long price,
            String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConfidenceJson(
            BigDecimal name,
            BigDecimal brand,
            BigDecimal category,
            @JsonProperty("model_name") BigDecimal modelName,
            BigDecimal color,
            BigDecimal price,
            BigDecimal description) {

        static ConfidenceJson empty() {
            return new ConfidenceJson(null, null, null, null, null, null, null);
        }
    }
}
