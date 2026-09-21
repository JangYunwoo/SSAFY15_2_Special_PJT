package com.gongguhan.domain.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.groupbuy.entity.GroupBuy;
import com.gongguhan.domain.groupbuy.repository.GroupBuyRepository;
import com.gongguhan.domain.participation.dto.DisputeRequest;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.domain.penalty.entity.SellerPenalty;
import com.gongguhan.domain.penalty.repository.SellerPenaltyRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ParticipationDisputeServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long SELLER_ID = 10L;
    private static final Long GROUP_BUY_ID = 100L;
    private static final Long PARTICIPATION_ID = 300L;

    private ParticipationRepository participationRepository;
    private GroupBuyRepository groupBuyRepository;
    private SellerPenaltyRepository sellerPenaltyRepository;
    private ParticipationDisputeService disputeService;

    @BeforeEach
    void setUp() {
        participationRepository = mock(ParticipationRepository.class);
        groupBuyRepository = mock(GroupBuyRepository.class);
        sellerPenaltyRepository = mock(SellerPenaltyRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-10T00:00:00Z"), ZoneOffset.UTC);
        disputeService = new ParticipationDisputeService(
                participationRepository, groupBuyRepository, sellerPenaltyRepository, clock);
    }

    private Participation stubOwnedParticipation() {
        Participation participation = mock(Participation.class);
        when(participation.getUserId()).thenReturn(USER_ID);
        when(participation.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(groupBuy.getSellerId()).thenReturn(SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));
        return participation;
    }

    @Test
    void disputeHoldsConfirmAndRegistersPenaltyCandidate() {
        Participation participation = stubOwnedParticipation();

        disputeService.submitDispute(USER_ID, PARTICIPATION_ID,
                new DisputeRequest("ITEM_MISMATCH", "다른 색상이 왔습니다"));

        verify(participation).holdConfirmForDispute();
        ArgumentCaptor<SellerPenalty> penaltyCaptor = ArgumentCaptor.forClass(SellerPenalty.class);
        verify(sellerPenaltyRepository).save(penaltyCaptor.capture());
        SellerPenalty penalty = penaltyCaptor.getValue();
        assertEquals(SellerPenalty.REASON_ITEM_MISMATCH, penalty.getReasonType());
        assertEquals(SellerPenalty.STATUS_CANDIDATE, penalty.getStatus());
        assertEquals(SELLER_ID, penalty.getSellerId());
        assertEquals(GROUP_BUY_ID, penalty.getGroupBuyId());
    }

    @Test
    void notDeliveredDisputeMapsToFakeInvoiceReason() {
        stubOwnedParticipation();

        disputeService.submitDispute(USER_ID, PARTICIPATION_ID,
                new DisputeRequest("NOT_DELIVERED", "배송완료라는데 받은 적이 없습니다"));

        ArgumentCaptor<SellerPenalty> penaltyCaptor = ArgumentCaptor.forClass(SellerPenalty.class);
        verify(sellerPenaltyRepository).save(penaltyCaptor.capture());
        assertEquals(SellerPenalty.REASON_FAKE_INVOICE, penaltyCaptor.getValue().getReasonType());
    }

    @Test
    void disputeRejectsUnknownType() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> disputeService.submitDispute(USER_ID, PARTICIPATION_ID,
                        new DisputeRequest("WRONG_TYPE", "사유")));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(sellerPenaltyRepository, never()).save(any());
    }

    @Test
    void disputeDeniedForOtherUsersParticipation() {
        Participation participation = mock(Participation.class);
        when(participation.getUserId()).thenReturn(2L);
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> disputeService.submitDispute(USER_ID, PARTICIPATION_ID,
                        new DisputeRequest("ITEM_MISMATCH", "사유")));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, exception.getErrorCode());
        verify(sellerPenaltyRepository, never()).save(any());
    }

    @Test
    void duplicateDisputeBlockedByConfirmHold() {
        Participation participation = Participation.create(GROUP_BUY_ID, USER_ID, 50000L, 3000L, 53000L,
                "홍길동", "010-1234-5678", "06236", "서울시 강남구", "101동");
        participation.markFulfilled();
        participation.markShipping();
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
        GroupBuy groupBuy = mock(GroupBuy.class);
        when(groupBuy.getGroupBuyId()).thenReturn(GROUP_BUY_ID);
        when(groupBuy.getSellerId()).thenReturn(SELLER_ID);
        when(groupBuyRepository.findById(GROUP_BUY_ID)).thenReturn(Optional.of(groupBuy));

        disputeService.submitDispute(USER_ID, PARTICIPATION_ID,
                new DisputeRequest("NOT_DELIVERED", "첫 접수"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> disputeService.submitDispute(USER_ID, PARTICIPATION_ID,
                        new DisputeRequest("NOT_DELIVERED", "중복 접수")));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION, exception.getErrorCode());
        verify(sellerPenaltyRepository, org.mockito.Mockito.times(1)).save(any());
    }
}
