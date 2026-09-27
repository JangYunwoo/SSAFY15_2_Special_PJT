package com.gongguhan.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.product.dto.AiField;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.repository.CategoryRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.ResourceAccessException;

class AiExtractionServiceTest {

    private ProductExtractionClient client;
    private ProductImageStorage imageStorage;
    private CategoryRepository categoryRepository;
    private AiExtractionService service;

    @BeforeEach
    void setUp() {
        client = mock(ProductExtractionClient.class);
        imageStorage = mock(ProductImageStorage.class);
        categoryRepository = mock(CategoryRepository.class);
        when(imageStorage.store(any())).thenAnswer(invocation -> {
            MockMultipartFile image = invocation.getArgument(0);
            return new ProductImageStorage.StoredImage(
                    image.getOriginalFilename(), "https://images.example.com/" + image.getOriginalFilename());
        });
        AiExtractionProperties properties =
                new AiExtractionProperties("http://localhost:8000", "/extract", 10000, 5);
        service = new AiExtractionService(client, imageStorage, categoryRepository, properties);
    }

    @Test
    void sendsAllUploadedImagesAndMapsCategoryNameToId() {
        MockMultipartFile first = image("main.jpg", "image/jpeg");
        MockMultipartFile second = image("detail.png", "image/png");
        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(12L);
        when(categoryRepository.findFirstByNameIgnoreCaseAndActiveTrue("생활"))
                .thenReturn(Optional.of(category));
        when(client.extract(List.of(
                "https://images.example.com/main.jpg",
                "https://images.example.com/detail.png"))).thenReturn(moduleResult("생활"));

        var response = service.extract(List.of(first, second));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.fields().categoryId().value()).isEqualTo("12");
        // 대표 한 장이 아니라 임시 저장한 이미지 전체 URL이 순서대로 전달된다.
        verify(client).extract(List.of(
                "https://images.example.com/main.jpg",
                "https://images.example.com/detail.png"));
        verify(imageStorage).deleteByUrl("https://images.example.com/main.jpg");
        verify(imageStorage).deleteByUrl("https://images.example.com/detail.png");
    }

    @Test
    void skipsEmptySlotsButKeepsRemainingImages() {
        MockMultipartFile first = image("main.jpg", "image/jpeg");
        MockMultipartFile empty = new MockMultipartFile("images", "", "image/jpeg", new byte[0]);
        MockMultipartFile third = image("detail.png", "image/png");
        when(client.extract(List.of(
                "https://images.example.com/main.jpg",
                "https://images.example.com/detail.png"))).thenReturn(moduleResult("생활"));

        service.extract(List.of(first, empty, third));

        verify(client).extract(List.of(
                "https://images.example.com/main.jpg",
                "https://images.example.com/detail.png"));
    }

    @Test
    void rejectsWhenImageCountExceedsMax() {
        List<MockMultipartFile> tooMany = List.of(
                image("1.jpg", "image/jpeg"), image("2.jpg", "image/jpeg"),
                image("3.jpg", "image/jpeg"), image("4.jpg", "image/jpeg"),
                image("5.jpg", "image/jpeg"), image("6.jpg", "image/jpeg"));

        assertThrows(BusinessException.class, () -> service.extract(List.copyOf(tooMany)));
        verifyNoInteractions(client);
        verifyNoInteractions(imageStorage);
    }

    @Test
    void unknownCategoryReturnsPartialResultAndWarning() {
        MockMultipartFile image = image("main.png", "image/png");
        when(categoryRepository.findFirstByNameIgnoreCaseAndActiveTrue("알 수 없는 품목"))
                .thenReturn(Optional.empty());
        when(client.extract(List.of("https://images.example.com/main.png")))
                .thenReturn(moduleResult("알 수 없는 품목"));

        var response = service.extract(List.of(image));

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.fields().categoryId()).isNull();
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("카테고리"));
    }

    @Test
    void timeoutReturnsFailedEmptyFieldsSoManualInputCanContinue() {
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        when(client.extract(List.of("https://images.example.com/main.jpg")))
                .thenThrow(new ResourceAccessException("timeout"));

        var response = service.extract(List.of(image));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.fields()).usingRecursiveComparison()
                .isEqualTo(com.gongguhan.domain.product.dto.AiExtractionFields.empty());
        assertThat(response.warnings()).containsExactly("AI 분석을 완료하지 못했습니다. 직접 입력해 주세요.");
        verify(imageStorage).deleteByUrl("https://images.example.com/main.jpg");
    }

    @Test
    void rejectsUnsupportedImageBeforeCallingAi() {
        assertThrows(BusinessException.class,
                () -> service.extract(List.of(image("main.webp", "image/webp"))));

        verifyNoInteractions(client);
        verifyNoInteractions(imageStorage);
    }

    @Test
    void nameNullWithCandidatesAndDirectCategoryId() throws Exception {
        // AI 담당이 준 실제 응답 계약: 상품명 미확정(name=null) + 후보 목록 + category_id 직접.
        String json = """
                {"status":"COMPLETED",
                 "result_json":{"name":null,
                                "name_candidates":["갤럭시 Z 플립6","갤럭시 Z 플립5"],
                                "category_id":10001},
                 "error_message":null}
                """;
        AiModuleExtraction parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, AiModuleExtraction.class);
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        when(client.extract(List.of("https://images.example.com/main.jpg"))).thenReturn(parsed);
        when(categoryRepository.existsById(10001L)).thenReturn(true);

        var response = service.extract(List.of(image));

        // 상품명은 확정 못 했으니 name=null, 후보는 그대로 전달, 카테고리는 직접 준 ID 사용.
        assertThat(response.fields().name()).isNull();
        assertThat(response.fields().nameCandidates())
                .containsExactly("갤럭시 Z 플립6", "갤럭시 Z 플립5");
        assertThat(response.fields().categoryId().value()).isEqualTo("10001");
        // name이 없으니 사용자가 골라야 하는 미완성 상태.
        assertThat(response.status()).isEqualTo("PARTIAL");
    }

    /**
     * AI 모듈은 confidence_json 을 보내지 않는다. 예전에는 없으면 0 으로 채웠고, 화면에
     * "신뢰도 0% · 값을 확인해 주세요" 가 떠서 정상 추출한 카테고리를 판매자가 틀린 값으로
     * 오해했다. 모르는 것과 0 은 다르므로 null 로 남긴다.
     */
    @Test
    void missingConfidenceStaysNullInsteadOfZero() throws Exception {
        String json = """
                {"status":"COMPLETED",
                 "result_json":{"name":"아이폰 15",
                                "name_candidates":[],
                                "category_id":10001,
                                "brand":"애플",
                                "description":"6.1인치 화면의 스마트폰입니다."},
                 "error_message":null}
                """;
        AiModuleExtraction parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, AiModuleExtraction.class);
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        when(client.extract(List.of("https://images.example.com/main.jpg"))).thenReturn(parsed);
        when(categoryRepository.existsById(10001L)).thenReturn(true);

        var response = service.extract(List.of(image));

        assertThat(response.fields().categoryId().confidence()).isNull();
        assertThat(response.fields().name().confidence()).isNull();
        assertThat(response.fields().brand().confidence()).isNull();
        // 값 자체는 정상적으로 전달된다.
        assertThat(response.fields().categoryId().value()).isEqualTo("10001");
        assertThat(response.fields().brand().value()).isEqualTo("애플");
    }

    /** AI 담당이 채우지 않던 상품 설명. 이제 result_json.description 으로 내려온다. */
    @Test
    void descriptionFromAiReachesResponse() throws Exception {
        String json = """
                {"status":"COMPLETED",
                 "result_json":{"name":"아이폰 15",
                                "name_candidates":[],
                                "category_id":10001,
                                "description":"6.1인치 화면의 스마트폰입니다. USB-C 단자를 사용합니다."},
                 "error_message":null}
                """;
        AiModuleExtraction parsed = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(json, AiModuleExtraction.class);
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        when(client.extract(List.of("https://images.example.com/main.jpg"))).thenReturn(parsed);
        when(categoryRepository.existsById(10001L)).thenReturn(true);

        var response = service.extract(List.of(image));

        assertThat(response.fields().description().value())
                .isEqualTo("6.1인치 화면의 스마트폰입니다. USB-C 단자를 사용합니다.");
    }

    @Test
    void nameConfirmedHasEmptyCandidates() {
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(12L);
        when(categoryRepository.findFirstByNameIgnoreCaseAndActiveTrue("생활"))
                .thenReturn(Optional.of(category));
        when(client.extract(List.of("https://images.example.com/main.jpg"))).thenReturn(moduleResult("생활"));

        var response = service.extract(List.of(image));

        assertThat(response.fields().name().value()).isEqualTo("상품");
        assertThat(response.fields().nameCandidates()).isEmpty();
        assertThat(response.status()).isEqualTo("COMPLETED");
    }

    @Test
    void partialStagingFailureDeletesAlreadyUploadedImageAndReturnsFailed() {
        MockMultipartFile first = image("main.jpg", "image/jpeg");
        MockMultipartFile second = image("detail.png", "image/png");
        doReturn(new ProductImageStorage.StoredImage(
                "main.jpg", "https://images.example.com/main.jpg"))
                .doThrow(new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "S3 unavailable"))
                .when(imageStorage).store(any());

        var response = service.extract(List.of(first, second));

        assertThat(response.status()).isEqualTo("FAILED");
        verify(imageStorage).deleteByUrl("https://images.example.com/main.jpg");
        verifyNoInteractions(client);
    }

    @Test
    void nonPublicStorageUrlSkipsAiAndDeletesStagedImage() {
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        doReturn(new ProductImageStorage.StoredImage(
                "main.jpg", "/api/v1/product-images/main.jpg"))
                .when(imageStorage).store(any());

        var response = service.extract(List.of(image));

        assertThat(response.status()).isEqualTo("FAILED");
        verify(imageStorage).deleteByUrl("/api/v1/product-images/main.jpg");
        verifyNoInteractions(client);
    }

    private AiModuleExtraction moduleResult(String category) {
        BigDecimal confidence = new BigDecimal("0.90");
        return new AiModuleExtraction(
                "COMPLETED",
                new AiModuleExtraction.Fields(
                        new AiField<>("상품", confidence),
                        null,
                        new AiField<>(category, confidence),
                        null,
                        null,
                        null,
                        null),
                List.of());
    }

    private MockMultipartFile image(String name, String contentType) {
        return new MockMultipartFile("images", name, contentType, new byte[] {1, 2, 3});
    }
}
