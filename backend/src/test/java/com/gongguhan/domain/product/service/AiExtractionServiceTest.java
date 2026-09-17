package com.gongguhan.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.product.dto.AiField;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.repository.CategoryRepository;
import com.gongguhan.global.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.ResourceAccessException;

class AiExtractionServiceTest {

    private ProductExtractionClient client;
    private CategoryRepository categoryRepository;
    private AiExtractionService service;

    @BeforeEach
    void setUp() {
        client = mock(ProductExtractionClient.class);
        categoryRepository = mock(CategoryRepository.class);
        AiExtractionProperties properties =
                new AiExtractionProperties("http://localhost:8000", "/extract", 10000, 5);
        service = new AiExtractionService(client, categoryRepository, properties);
    }

    @Test
    void sendsAllUploadedImagesAndMapsCategoryNameToId() {
        MockMultipartFile first = image("main.jpg", "image/jpeg");
        MockMultipartFile second = image("detail.png", "image/png");
        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(12L);
        when(categoryRepository.findFirstByNameIgnoreCaseAndActiveTrue("생활"))
                .thenReturn(Optional.of(category));
        when(client.extract(List.of(first, second))).thenReturn(moduleResult("생활"));

        var response = service.extract(List.of(first, second));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.fields().categoryId().value()).isEqualTo("12");
        // 대표 한 장이 아니라 올린 이미지 전체가 그대로 전달된다.
        verify(client).extract(List.of(first, second));
    }

    @Test
    void skipsEmptySlotsButKeepsRemainingImages() {
        MockMultipartFile first = image("main.jpg", "image/jpeg");
        MockMultipartFile empty = new MockMultipartFile("images", "", "image/jpeg", new byte[0]);
        MockMultipartFile third = image("detail.png", "image/png");
        when(client.extract(List.of(first, third))).thenReturn(moduleResult("생활"));

        service.extract(List.of(first, empty, third));

        verify(client).extract(List.of(first, third));
    }

    @Test
    void rejectsWhenImageCountExceedsMax() {
        List<MockMultipartFile> tooMany = List.of(
                image("1.jpg", "image/jpeg"), image("2.jpg", "image/jpeg"),
                image("3.jpg", "image/jpeg"), image("4.jpg", "image/jpeg"),
                image("5.jpg", "image/jpeg"), image("6.jpg", "image/jpeg"));

        assertThrows(BusinessException.class, () -> service.extract(List.copyOf(tooMany)));
        verifyNoInteractions(client);
    }

    @Test
    void unknownCategoryReturnsPartialResultAndWarning() {
        MockMultipartFile image = image("main.png", "image/png");
        when(categoryRepository.findFirstByNameIgnoreCaseAndActiveTrue("알 수 없는 품목"))
                .thenReturn(Optional.empty());
        when(client.extract(List.of(image))).thenReturn(moduleResult("알 수 없는 품목"));

        var response = service.extract(List.of(image));

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.fields().categoryId()).isNull();
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("카테고리"));
    }

    @Test
    void timeoutReturnsFailedEmptyFieldsSoManualInputCanContinue() {
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        when(client.extract(List.of(image))).thenThrow(new ResourceAccessException("timeout"));

        var response = service.extract(List.of(image));

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.fields()).usingRecursiveComparison()
                .isEqualTo(com.gongguhan.domain.product.dto.AiExtractionFields.empty());
        assertThat(response.warnings()).containsExactly("AI 분석을 완료하지 못했습니다. 직접 입력해 주세요.");
    }

    @Test
    void rejectsUnsupportedImageBeforeCallingAi() {
        assertThrows(BusinessException.class,
                () -> service.extract(List.of(image("main.webp", "image/webp"))));

        verifyNoInteractions(client);
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
        when(client.extract(List.of(image))).thenReturn(parsed);
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

    @Test
    void nameConfirmedHasEmptyCandidates() {
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(12L);
        when(categoryRepository.findFirstByNameIgnoreCaseAndActiveTrue("생활"))
                .thenReturn(Optional.of(category));
        when(client.extract(List.of(image))).thenReturn(moduleResult("생활"));

        var response = service.extract(List.of(image));

        assertThat(response.fields().name().value()).isEqualTo("상품");
        assertThat(response.fields().nameCandidates()).isEmpty();
        assertThat(response.status()).isEqualTo("COMPLETED");
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
