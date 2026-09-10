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
        service = new AiExtractionService(client, categoryRepository);
    }

    @Test
    void sendsOnlyRepresentativeImageAndMapsCategoryNameToId() {
        MockMultipartFile first = image("main.jpg", "image/jpeg");
        MockMultipartFile second = image("detail.png", "image/png");
        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(12L);
        when(categoryRepository.findFirstByNameIgnoreCase("생활"))
                .thenReturn(Optional.of(category));
        when(client.extract(first)).thenReturn(moduleResult("생활"));

        var response = service.extract(List.of(first, second));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.fields().categoryId().value()).isEqualTo("12");
        verify(client).extract(first);
    }

    @Test
    void unknownCategoryReturnsPartialResultAndWarning() {
        MockMultipartFile image = image("main.png", "image/png");
        when(categoryRepository.findFirstByNameIgnoreCase("알 수 없는 품목"))
                .thenReturn(Optional.empty());
        when(client.extract(image)).thenReturn(moduleResult("알 수 없는 품목"));

        var response = service.extract(List.of(image));

        assertThat(response.status()).isEqualTo("PARTIAL");
        assertThat(response.fields().categoryId()).isNull();
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("카테고리"));
    }

    @Test
    void timeoutReturnsFailedEmptyFieldsSoManualInputCanContinue() {
        MockMultipartFile image = image("main.jpg", "image/jpeg");
        when(client.extract(image)).thenThrow(new ResourceAccessException("timeout"));

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
