package com.gongguhan.domain.product.service;

import com.gongguhan.domain.product.dto.AiExtractionFields;
import com.gongguhan.domain.product.dto.AiExtractionResponse;
import com.gongguhan.domain.product.dto.AiField;
import com.gongguhan.domain.product.repository.CategoryRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AiExtractionService {

    private static final Logger log = LoggerFactory.getLogger(AiExtractionService.class);

    private final ProductExtractionClient extractionClient;
    private final CategoryRepository categoryRepository;

    public AiExtractionService(
            ProductExtractionClient extractionClient,
            CategoryRepository categoryRepository) {
        this.extractionClient = extractionClient;
        this.categoryRepository = categoryRepository;
    }

    public AiExtractionResponse extract(List<MultipartFile> images) {
        MultipartFile representativeImage = representativeImage(images);
        try {
            AiModuleExtraction result = extractionClient.extract(representativeImage);
            return map(result);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("AI 상품 정보 추출 실패 file={}", representativeImage.getOriginalFilename(), e);
            return AiExtractionResponse.failed("AI 분석을 완료하지 못했습니다. 직접 입력해 주세요.");
        }
    }

    private MultipartFile representativeImage(List<MultipartFile> images) {
        if (images == null || images.isEmpty() || images.get(0).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "대표 이미지 한 장이 필요합니다.");
        }
        MultipartFile image = images.get(0);
        if (!MediaTypeValidator.isJpegOrPng(image.getContentType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "JPG 또는 PNG 이미지만 분석할 수 있습니다.");
        }
        return image;
    }

    private AiExtractionResponse map(AiModuleExtraction result) {
        if (result == null || result.normalizedFields() == null || "FAILED".equalsIgnoreCase(result.status())) {
            return AiExtractionResponse.failed("AI 분석 결과가 비어 있습니다. 직접 입력해 주세요.");
        }
        AiModuleExtraction.Fields source = result.normalizedFields();
        List<String> warnings = new ArrayList<>(result.warnings() == null ? List.of() : result.warnings());
        if (result.errorMessage() != null && !result.errorMessage().isBlank()) {
            warnings.add(result.errorMessage());
        }
        AiField<String> categoryId = mapCategory(source.category(), warnings);
        AiExtractionFields fields = new AiExtractionFields(
                source.name(), source.brand(), categoryId, source.modelName(), source.color(),
                source.price(), source.description());
        String status = hasAllCoreFields(fields) ? "COMPLETED" : "PARTIAL";
        return new AiExtractionResponse(status, fields, List.copyOf(warnings));
    }

    private AiField<String> mapCategory(AiField<String> category, List<String> warnings) {
        if (category == null || category.value() == null || category.value().isBlank()) {
            return null;
        }
        return categoryRepository.findFirstByNameIgnoreCaseAndActiveTrue(category.value().trim())
                .map(value -> new AiField<>(String.valueOf(value.getCategoryId()), category.confidence()))
                .orElseGet(() -> {
                    warnings.add("카테고리를 자동으로 매칭하지 못했습니다. 직접 선택해 주세요.");
                    return null;
                });
    }

    private boolean hasAllCoreFields(AiExtractionFields fields) {
        return fields.name() != null && fields.categoryId() != null;
    }

    private static final class MediaTypeValidator {
        private MediaTypeValidator() {
        }

        static boolean isJpegOrPng(String contentType) {
            return "image/jpeg".equalsIgnoreCase(contentType) || "image/png".equalsIgnoreCase(contentType);
        }
    }
}
