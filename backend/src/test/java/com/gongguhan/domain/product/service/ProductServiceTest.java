package com.gongguhan.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.product.dto.ProductUpsertRequest;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.repository.CategoryRepository;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.domain.product.repository.WithdrawalRestrictionReasonRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

class ProductServiceTest {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private ProductImageStorage imageStorage;
    private WithdrawalRestrictionReasonRepository restrictionReasonRepository;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        imageStorage = mock(ProductImageStorage.class);
        restrictionReasonRepository = mock(WithdrawalRestrictionReasonRepository.class);
        productService = new ProductService(
                productRepository, categoryRepository, imageStorage, restrictionReasonRepository);
    }

    @Test
    void createStoresImagesAndAssignsExactlyOneThumbnail() {
        Category category = category(1L, "가전");
        when(productRepository.countUsableSeller(10L)).thenReturn(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(imageStorage.store(any())).thenReturn(
                new ProductImageStorage.StoredImage("one.jpg", "/api/v1/product-images/one.jpg"),
                new ProductImageStorage.StoredImage("two.png", "/api/v1/product-images/two.png"));
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        productService.create(10L, request(1), List.of(image("one.jpg"), image("two.png")));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(captor.capture());
        Product product = captor.getValue();
        assertEquals(2, product.getImages().size());
        assertEquals(1, product.getImages().stream().filter(image -> image.isThumbnail()).count());
        assertEquals("/api/v1/product-images/two.png", product.getImages().get(1).getImageUrl());
    }

    @Test
    void suspendedSellerCannotCreateProduct() {
        when(productRepository.countUsableSeller(10L)).thenReturn(0L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.create(10L, request(0), List.of(image("one.jpg"))));

        assertEquals(ErrorCode.SELLER_SUSPENDED, exception.getErrorCode());
        verify(imageStorage, never()).store(any());
    }

    @Test
    void productLinkedToActiveGroupBuyCannotBeUpdated() {
        Product product = Product.create(10L, category(1L, "가전"), "상품", null, null, null, null, 1000L);
        when(productRepository.countUsableSeller(10L)).thenReturn(1L);
        when(productRepository.findByProductIdAndSellerIdAndStatus(1L, 10L, Product.STATUS_ACTIVE))
                .thenReturn(Optional.of(product));
        when(productRepository.countActiveGroupBuys(1L)).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.update(10L, 1L, request(0), null));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void invalidThumbnailIndexIsRejectedBeforeUploading() {
        Category category = category(1L, "가전");
        when(productRepository.countUsableSeller(10L)).thenReturn(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.create(10L, request(2), List.of(image("one.jpg"))));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(imageStorage, never()).store(any());
    }

    @Test
    void evidenceRequiredReasonRejectsProductWithoutEvidenceUrl() {
        Category category = category(1L, "가전");
        var reason = mock(com.gongguhan.domain.product.entity.WithdrawalRestrictionReason.class);
        when(productRepository.countUsableSeller(10L)).thenReturn(1L);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(restrictionReasonRepository.findById(3L)).thenReturn(Optional.of(reason));
        when(reason.isActive()).thenReturn(true);
        when(reason.isRequiresEvidence()).thenReturn(true);

        ProductUpsertRequest restricted = new ProductUpsertRequest(
                1L, "상품", "브랜드", "모델", "검정", "설명", 1000L, 0, false, 3L, null);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> productService.create(10L, restricted, List.of(image("one.jpg"))));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(imageStorage, never()).store(any());
    }

    private ProductUpsertRequest request(int thumbnailIndex) {
        return new ProductUpsertRequest(
                1L, "상품", "브랜드", "모델", "검정", "설명", 1000L, thumbnailIndex,
                false, null, null);
    }

    private MockMultipartFile image(String name) {
        return new MockMultipartFile("images", name, "image/jpeg", new byte[] {1});
    }

    private Category category(Long id, String name) {
        Category category = mock(Category.class);
        when(category.getCategoryId()).thenReturn(id);
        when(category.getName()).thenReturn(name);
        return category;
    }
}
