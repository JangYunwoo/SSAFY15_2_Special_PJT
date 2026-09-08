package com.gongguhan.domain.groupbuy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewRequest;
import com.gongguhan.domain.groupbuy.dto.GroupBuyAmountPreviewResponse;
import com.gongguhan.domain.groupbuy.dto.GroupBuyCreateRequest;
import com.gongguhan.domain.groupbuy.repository.GroupBuyCommandRepository;
import com.gongguhan.domain.groupbuy.repository.GroupBuyCreateCommand;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GroupBuyServiceTest {

    private GroupBuyCommandRepository groupBuyCommandRepository;
    private ProductRepository productRepository;
    private GroupBuyService groupBuyService;

    @BeforeEach
    void setUp() {
        groupBuyCommandRepository = mock(GroupBuyCommandRepository.class);
        productRepository = mock(ProductRepository.class);
        groupBuyService = new GroupBuyService(groupBuyCommandRepository, productRepository);
    }

    @Test
    void previewRoundsUpShareAndRoundsDownPlatformFee() {
        stubUsableSellerAndOwnedProduct();

        GroupBuyAmountPreviewResponse response = groupBuyService.preview(
                10L, new GroupBuyAmountPreviewRequest(1L, 1_000_001L, 20, 4_000L));

        assertEquals(50_001L, response.unitShareAmount());
        assertEquals(19L, response.roundingSurplus());
        assertEquals(50_001L, response.estimatedPlatformFee());
        assertEquals(1_030_019L, response.estimatedSettlementAmount());
        assertEquals("RATE", response.feePolicy());
    }

    @Test
    void createPersistsCalculatedAmountsAsSnapshot() {
        stubUsableSellerAndOwnedProduct();
        when(groupBuyCommandRepository.create(any(GroupBuyCreateCommand.class)))
                .thenReturn(new GroupBuyCommandRepository.StoredGroupBuy(
                        1L, LocalDateTime.of(2026, 9, 5, 0, 0), LocalDateTime.of(2026, 9, 5, 0, 0)));
        OffsetDateTime startAt = OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime endAt = OffsetDateTime.of(2026, 9, 12, 23, 59, 0, 0, ZoneOffset.UTC);

        groupBuyService.create(
                10L, new GroupBuyCreateRequest(1L, 1_000_001L, 20, 4_000L, startAt, endAt));

        ArgumentCaptor<GroupBuyCreateCommand> captor = ArgumentCaptor.forClass(GroupBuyCreateCommand.class);
        verify(groupBuyCommandRepository).create(captor.capture());
        GroupBuyCreateCommand saved = captor.getValue();
        assertEquals(50_001L, saved.unitShareAmount());
        assertEquals(19L, saved.roundingDiff());
        assertEquals(50_001L, saved.expectedPlatformFee());
        assertEquals(1_030_019L, saved.expectedSettlementAmount());
    }

    @Test
    void createRejectsRecruitEndThatIsNotAfterStart() {
        stubUsableSellerAndOwnedProduct();
        OffsetDateTime sameTime = OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupBuyService.create(
                        10L,
                        new GroupBuyCreateRequest(1L, 1_000_000L, 20, 4_000L, sameTime, sameTime)));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(groupBuyCommandRepository, never()).create(any(GroupBuyCreateCommand.class));
    }

    @Test
    void suspendedSellerCannotPreviewAmounts() {
        when(productRepository.countUsableSeller(10L)).thenReturn(0L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupBuyService.preview(
                        10L, new GroupBuyAmountPreviewRequest(1L, 1_000_000L, 20, 4_000L)));

        assertEquals(ErrorCode.SELLER_SUSPENDED, exception.getErrorCode());
        verify(productRepository, never())
                .findByProductIdAndSellerIdAndStatus(any(), any(), any());
    }

    private void stubUsableSellerAndOwnedProduct() {
        Product product = Product.create(
                10L, mock(Category.class), "상품", null, null, null, null, 1_000_000L);
        when(productRepository.countUsableSeller(10L)).thenReturn(1L);
        when(productRepository.findByProductIdAndSellerIdAndStatus(1L, 10L, Product.STATUS_ACTIVE))
                .thenReturn(Optional.of(product));
    }
}
