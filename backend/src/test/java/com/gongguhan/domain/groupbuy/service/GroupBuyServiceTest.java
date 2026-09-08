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
import com.gongguhan.domain.groupbuy.dto.GroupBuySearchCondition;
import com.gongguhan.domain.groupbuy.dto.GroupBuySort;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyQueryRepository;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.product.entity.Category;
import com.gongguhan.domain.product.entity.Product;
import com.gongguhan.domain.product.repository.ProductRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GroupBuyServiceTest {

    private GroupBuyRepository groupBuyRepository;
    private GroupBuyQueryRepository groupBuyQueryRepository;
    private ProductRepository productRepository;
    private GroupBuyService groupBuyService;

    @BeforeEach
    void setUp() {
        groupBuyRepository = mock(GroupBuyRepository.class);
        groupBuyQueryRepository = mock(GroupBuyQueryRepository.class);
        productRepository = mock(ProductRepository.class);
        groupBuyService = new GroupBuyService(groupBuyRepository, groupBuyQueryRepository, productRepository);
    }

    @Test
    void publicListNormalizesSearchConditionAndUsesRequestedPage() {
        when(groupBuyQueryRepository.findPublicGroupBuys(any(), any())).thenReturn(Page.empty());

        groupBuyService.getPublicGroupBuys("  무선 키보드  ", 11L, null, "deadline", 2, 30);

        ArgumentCaptor<GroupBuySearchCondition> conditionCaptor =
                ArgumentCaptor.forClass(GroupBuySearchCondition.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(groupBuyQueryRepository).findPublicGroupBuys(conditionCaptor.capture(), pageableCaptor.capture());
        assertEquals("무선 키보드", conditionCaptor.getValue().keyword());
        assertEquals(11L, conditionCaptor.getValue().categoryId());
        assertEquals(GroupBuy.STATUS_RECRUITING, conditionCaptor.getValue().status());
        assertEquals(GroupBuySort.DEADLINE, conditionCaptor.getValue().sort());
        assertEquals(2, pageableCaptor.getValue().getPageNumber());
        assertEquals(30, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void publicListRejectsUnsupportedSort() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupBuyService.getPublicGroupBuys(null, null, null, "random", 0, 20));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(groupBuyQueryRepository, never()).findPublicGroupBuys(any(), any());
    }

    @Test
    void publicDetailReturnsNotFoundForMissingGroupBuy() {
        when(groupBuyQueryRepository.findPublicDetail(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> groupBuyService.getPublicDetail(99L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void buyerParticipationListNormalizesStatus() {
        when(groupBuyQueryRepository.findBuyerParticipations(any(), any(), any())).thenReturn(Page.empty());

        groupBuyService.getBuyerParticipations(20L, "delivered", 0, 20);

        verify(groupBuyQueryRepository).findBuyerParticipations(
                eq(20L), eq("DELIVERED"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listRejectsPageSizeOverOneHundred() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupBuyService.getSellerGroupBuys(10L, 0, 101));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(groupBuyQueryRepository, never()).findSellerGroupBuys(any(), any());
    }

    @Test
    void sellerCannotReadParticipantsOfAnotherSellersGroupBuy() {
        when(groupBuyQueryRepository.existsOwnedGroupBuy(10L, 1L)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupBuyService.getSellerParticipants(10L, 1L, 0, 20));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(groupBuyQueryRepository, never()).findSellerParticipants(any(), any(), any());
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
        when(groupBuyRepository.saveAndFlush(any(GroupBuy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        OffsetDateTime startAt = OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime endAt = OffsetDateTime.of(2026, 9, 12, 23, 59, 0, 0, ZoneOffset.UTC);

        groupBuyService.create(
                10L, new GroupBuyCreateRequest(1L, 1_000_001L, 20, 4_000L, startAt, endAt));

        ArgumentCaptor<GroupBuy> captor = ArgumentCaptor.forClass(GroupBuy.class);
        verify(groupBuyRepository).saveAndFlush(captor.capture());
        GroupBuy saved = captor.getValue();
        assertEquals(50_001L, saved.getUnitShareAmount());
        assertEquals(19L, saved.getRoundingDiff());
        assertEquals(50_001L, saved.getExpectedPlatformFee());
        assertEquals(1_030_019L, saved.getExpectedSettlementAmount());
        assertEquals(GroupBuy.STATUS_RECRUITING, saved.getStatus());
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
        verify(groupBuyRepository, never()).saveAndFlush(any(GroupBuy.class));
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
