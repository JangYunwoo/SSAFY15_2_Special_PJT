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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResultJson(
            String name,
            String brand,
            @JsonProperty("category_name") String categoryName,
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
