package com.gongguhan.domain.product.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.entity.RestrictionReviewStatus;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.domain.product.repository.WithdrawalRestrictionReasonRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WithdrawalRestrictionServiceTest {

    private ProductRepository productRepository;
    private WithdrawalRestrictionService service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        service = new WithdrawalRestrictionService(
                mock(WithdrawalRestrictionReasonRepository.class),
                productRepository,
                Clock.fixed(Instant.parse("2026-09-10T01:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void adminCanApprovePendingRestriction() {
        Product product = mock(Product.class);
        Category category = mock(Category.class);
        when(product.isWithdrawalRestricted()).thenReturn(true);
        when(product.getCategory()).thenReturn(category);
        when(product.getRestrictionReviewStatus()).thenReturn(RestrictionReviewStatus.APPROVED);
        when(product.getImages()).thenReturn(List.of());
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        service.review(99L, 1L, RestrictionReviewStatus.APPROVED);

        verify(product).reviewWithdrawalRestriction(
                99L, RestrictionReviewStatus.APPROVED, java.time.LocalDateTime.of(2026, 9, 10, 1, 0));
    }

    @Test
    void noneCannotBeUsedAsReviewDecision() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review(99L, 1L, RestrictionReviewStatus.NONE));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }

    @Test
    void repeatedReviewIsReportedAsInvalidTransition() {
        Product product = mock(Product.class);
        when(product.isWithdrawalRestricted()).thenReturn(true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        org.mockito.Mockito.doThrow(new IllegalStateException("이미 검토됨"))
                .when(product).reviewWithdrawalRestriction(any(), any(), any());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.review(99L, 1L, RestrictionReviewStatus.REJECTED));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
    }
}
