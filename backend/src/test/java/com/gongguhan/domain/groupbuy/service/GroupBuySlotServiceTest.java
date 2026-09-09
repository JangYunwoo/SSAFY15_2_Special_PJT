package com.gongguhan.domain.groupbuy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.groupbuy.dto.GroupBuySlotSnapshot;
import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupBuySlotServiceTest {

    private static final Long GROUP_BUY_ID = 100L;

    private GroupBuyRepository groupBuyRepository;
    private GroupBuyRecruitmentService groupBuyRecruitmentService;
    private GroupBuy groupBuy;
    private GroupBuySlotService groupBuySlotService;

    @BeforeEach
    void setUp() {
        groupBuyRepository = mock(GroupBuyRepository.class);
        groupBuyRecruitmentService = mock(GroupBuyRecruitmentService.class);
        groupBuy = mock(GroupBuy.class);
        groupBuySlotService = new GroupBuySlotService(groupBuyRepository, groupBuyRecruitmentService);
    }

    @Test
    void reserveUsesPessimisticLockAndReturnsSnapshot() {
        stubLockedGroupBuy();
        when(groupBuy.getReservedSlots()).thenReturn(1);

        GroupBuySlotSnapshot result = groupBuySlotService.reserve(GROUP_BUY_ID);

        verify(groupBuyRepository).findWithLockByGroupBuyId(GROUP_BUY_ID);
        verify(groupBuy).reserveSlot();
        assertEquals(GROUP_BUY_ID, result.groupBuyId());
        assertEquals(50_000L, result.unitShareAmount());
        assertEquals(4_000L, result.shippingFee());
        assertEquals(1, result.reservedSlots());
    }

    @Test
    void releaseUsesPessimisticLock() {
        stubLockedGroupBuy();

        groupBuySlotService.release(GROUP_BUY_ID);

        verify(groupBuyRepository).findWithLockByGroupBuyId(GROUP_BUY_ID);
        verify(groupBuy).releaseSlot();
    }

    @Test
    void confirmUsesPessimisticLock() {
        stubLockedGroupBuy();

        groupBuySlotService.confirm(GROUP_BUY_ID);

        verify(groupBuyRepository).findWithLockByGroupBuyId(GROUP_BUY_ID);
        verify(groupBuy).confirmParticipant();
        verify(groupBuyRecruitmentService).judgeTargetReached(groupBuy);
    }

    @Test
    void getDoesNotAcquireWriteLock() {
        stubGroupBuy();
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        groupBuySlotService.get(GROUP_BUY_ID);

        verify(groupBuyRepository).findById(GROUP_BUY_ID);
    }

    @Test
    void missingGroupBuyReturnsResourceNotFound() {
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class, () -> groupBuySlotService.reserve(GROUP_BUY_ID));

        assertEquals(ErrorCode.RESOURCE_NOT_FOUND, exception.getErrorCode());
    }

    private void stubLockedGroupBuy() {
        stubGroupBuy();
        when(groupBuyRepository.findWithLockByGroupBuyId(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
    }

    private void stubGroupBuy() {
        when(groupBuy.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(groupBuy.getUnitShareAmount()).thenReturn(50_000L);
        when(groupBuy.getShippingFee()).thenReturn(4_000L);
        when(groupBuy.getTargetParticipants()).thenReturn(20);
        when(groupBuy.getCurrentParticipants()).thenReturn(3);
        when(groupBuy.getRecruitEndAt()).thenReturn(LocalDateTime.of(2026, 9, 30, 23, 59));
        when(groupBuy.getStatus()).thenReturn(GroupBuy.STATUS_RECRUITING);
    }
}
