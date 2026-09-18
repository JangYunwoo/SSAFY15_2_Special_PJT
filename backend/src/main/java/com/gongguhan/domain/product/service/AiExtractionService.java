package com.gongguhan.domain.product.service;

import com.gongguhan.domain.product.dto.AiExtractionFields;
import com.gongguhan.domain.product.dto.AiExtractionResponse;
import com.gongguhan.domain.product.dto.AiField;
import com.gongguhan.domain.product.repository.CategoryRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.net.URI;
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
    private final ProductImageStorage imageStorage;
    private final CategoryRepository categoryRepository;
    private final AiExtractionProperties properties;

    public AiExtractionService(
            ProductExtractionClient extractionClient,
            ProductImageStorage imageStorage,
            CategoryRepository categoryRepository,
            AiExtractionProperties properties) {
        this.extractionClient = extractionClient;
        this.imageStorage = imageStorage;
        this.categoryRepository = categoryRepository;
        this.properties = properties;
    }

    /**
     * 업로드된 이미지 <b>전체</b>를 임시 저장한 뒤 AI 모듈로 보내 상품 필드를 추출한다.
     *
     * <p>여러 장을 함께 분석하면 대표 한 장만으로는 안 보이던 브랜드·모델명·상세 스펙을
     * 더 잘 잡아낸다(AI 파트 요청). 빈 슬롯은 걸러내고, 대표(첫 장)를 포함해 순서를 유지한다.
     * AI 서버는 Gemini가 읽을 수 있는 S3 HTTPS URL을 요구하므로 분석용 객체를 먼저 올린다.
     * 이 객체는 상품 등록 이미지가 아니며, 성공·실패와 관계없이 요청 종료 전에 삭제한다.
     */
    public AiExtractionResponse extract(List<MultipartFile> images) {
        List<MultipartFile> validImages = validImages(images);
        List<ProductImageStorage.StoredImage> stagedImages = new ArrayList<>();
        try {
            for (MultipartFile image : validImages) {
                stagedImages.add(imageStorage.store(image));
            }
            List<String> imageUrls = stagedImages.stream()
                    .map(ProductImageStorage.StoredImage::publicUrl)
                    .toList();
            requirePublicHttpsUrls(imageUrls);
            AiModuleExtraction result = extractionClient.extract(imageUrls);
            return map(result);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.VALIDATION_ERROR) {
                throw e;
            }
            log.warn("AI 분석용 이미지 저장 실패 imageCount={}", validImages.size(), e);
            return AiExtractionResponse.failed("AI 분석을 완료하지 못했습니다. 직접 입력해 주세요.");
        } catch (RestClientException | IllegalStateException e) {
            log.warn("AI 상품 정보 추출 실패 imageCount={}", validImages.size(), e);
            return AiExtractionResponse.failed("AI 분석을 완료하지 못했습니다. 직접 입력해 주세요.");
        } finally {
            deleteStagedImages(stagedImages);
        }
    }

    private void requirePublicHttpsUrls(List<String> imageUrls) {
        for (String imageUrl : imageUrls) {
            try {
                URI uri = URI.create(imageUrl);
                String host = uri.getHost();
                boolean localHost = host != null && ("localhost".equalsIgnoreCase(host)
                        || host.contains(":"));
                boolean ipAddress = host != null && host.matches("[0-9.]+");
                if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                        || uri.getUserInfo() != null || uri.getFragment() != null
                        || localHost || ipAddress) {
                    throw new IllegalStateException("AI 분석에는 외부 공개 HTTPS 이미지 URL이 필요합니다.");
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("AI 분석용 이미지 URL이 올바르지 않습니다.", e);
            }
        }
    }

    private void deleteStagedImages(List<ProductImageStorage.StoredImage> stagedImages) {
        for (ProductImageStorage.StoredImage stagedImage : stagedImages) {
            try {
                imageStorage.deleteByUrl(stagedImage.publicUrl());
            } catch (RuntimeException e) {
                log.warn("AI 분석용 임시 이미지 삭제 실패 storedName={}", stagedImage.storedName(), e);
            }
        }
    }

    /**
     * 분석에 보낼 이미지를 추린다. 빈 슬롯(미첨부)은 제거하고, 남은 것은 모두 JPG/PNG 여야 하며
     * 최소 한 장(대표) 이상, {@code app.ai-extraction.max-images} 이하여야 한다.
     */
    private List<MultipartFile> validImages(List<MultipartFile> images) {
        List<MultipartFile> present = images == null ? List.of()
                : images.stream().filter(image -> image != null && !image.isEmpty()).toList();
        if (present.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "대표 이미지 한 장이 필요합니다.");
        }
        if (present.size() > properties.maxImages()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "이미지는 최대 " + properties.maxImages() + "장까지 분석할 수 있습니다.");
        }
        for (MultipartFile image : present) {
            if (!MediaTypeValidator.isJpegOrPng(image.getContentType())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "JPG 또는 PNG 이미지만 분석할 수 있습니다.");
            }
        }
        return present;
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
        // 카테고리: AI가 category_id를 직접 주면 그것을 우선 쓰고(존재 확인), 없으면 category_name 매칭.
        AiField<String> categoryId = resolveCategory(result, source.category(), warnings);
        // 상품명 후보: AI가 상품명을 확정하지 못하면(name=null) 후보 목록을 프론트에 전달한다.
        List<String> nameCandidates = result.nameCandidates();
        AiExtractionFields fields = new AiExtractionFields(
                source.name(), List.copyOf(nameCandidates), source.brand(), categoryId,
                source.modelName(), source.color(), source.price(), source.description());
        String status = hasAllCoreFields(fields) ? "COMPLETED" : "PARTIAL";
        return new AiExtractionResponse(status, fields, List.copyOf(warnings));
    }

    /**
     * 카테고리를 결정한다. 신규 계약({@code result_json.category_id})이 오면 그 ID의 유효성만
     * 확인해 그대로 쓰고, 없으면 기존처럼 {@code category_name}을 카탈로그와 이름으로 매칭한다.
     */
    private AiField<String> resolveCategory(AiModuleExtraction result, AiField<String> categoryName,
                                            List<String> warnings) {
        Long directId = result.directCategoryId();
        if (directId != null) {
            if (categoryRepository.existsById(directId)) {
                return new AiField<>(String.valueOf(directId),
                        categoryName == null ? java.math.BigDecimal.ZERO : categoryName.confidence());
            }
            warnings.add("AI가 준 카테고리를 찾을 수 없습니다. 직접 선택해 주세요.");
            return null;
        }
        return mapCategory(categoryName, warnings);
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
