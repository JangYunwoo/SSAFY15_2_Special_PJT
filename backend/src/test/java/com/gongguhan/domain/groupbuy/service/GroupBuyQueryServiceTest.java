package com.gongguhan.domain.groupbuy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.groupbuy.dto.GroupBuySearchCondition;
import com.gongguhan.domain.groupbuy.dto.GroupBuySort;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyQueryRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class GroupBuyQueryServiceTest {

    private GroupBuyQueryRepository groupBuyQueryRepository;
    private GroupBuyQueryService groupBuyQueryService;

    @BeforeEach
    void setUp() {
        groupBuyQueryRepository = mock(GroupBuyQueryRepository.class);
        groupBuyQueryService = new GroupBuyQueryService(groupBuyQueryRepository);
    }

    @Test
    void publicListNormalizesSearchConditionAndUsesRequestedPage() {
        when(groupBuyQueryRepository.findPublicGroupBuys(any(), any())).thenReturn(Page.empty());

        groupBuyQueryService.getPublicGroupBuys("  무선 키보드  ", 11L, null, "deadline", 2, 30);

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
                () -> groupBuyQueryService.getPublicGroupBuys(null, null, null, "random", 0, 20));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(groupBuyQueryRepository, never()).findPublicGroupBuys(any(), any());
    }

    @Test
    void publicDetailReturnsNotFoundForMissingGroupBuy() {
        when(groupBuyQueryRepository.findPublicDetail(99L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> groupBuyQueryService.getPublicDetail(99L));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void listRejectsPageSizeOverOneHundred() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupBuyQueryService.getSellerGroupBuys(10L, 0, 101));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(groupBuyQueryRepository, never()).findSellerGroupBuys(any(), any());
    }

    @Test
    void sellerCannotReadParticipantsOfAnotherSellersGroupBuy() {
        when(groupBuyQueryRepository.existsOwnedGroupBuy(10L, 1L)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> groupBuyQueryService.getSellerParticipants(10L, 1L, 0, 20));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
        verify(groupBuyQueryRepository, never()).findSellerParticipants(any(), any(), any());
    }
}
