package com.gongguhan.domain.participation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gongguhan.domain.participation.dto.PurchaseConfirmResponse;
import com.gongguhan.domain.participation.entity.Participation;
import com.gongguhan.domain.participation.repository.ParticipationRepository;
import com.gongguhan.global.error.BusinessException;
import com.gongguhan.global.error.ErrorCode;
import com.gongguhan.global.policy.PolicyKey;
import com.gongguhan.global.policy.PolicyService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PurchaseConfirmServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long PARTICIPATION_ID = 300L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 10, 0, 0);

    private ParticipationRepository participationRepository;
    private PolicyService policyService;
    private PurchaseConfirmService purchaseConfirmService;

    @BeforeEach
    void setUp() {
        participationRepository = mock(ParticipationRepository.class);
        policyService = mock(PolicyService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-10T00:00:00Z"), ZoneOffset.UTC);
        purchaseConfirmService =
                new PurchaseConfirmService(participationRepository, policyService, clock);
    }

    @Test
    void confirmManuallyMarksParticipationConfirmed() {
        Participation participation = mock(Participation.class);
        when(participation.getUserId()).thenReturn(USER_ID);
        when(participation.getStatus()).thenReturn(Participation.STATUS_CONFIRMED);
        when(participation.getConfirmType()).thenReturn(Participation.CONFIRM_TYPE_MANUAL);
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        PurchaseConfirmResponse response =
                purchaseConfirmService.confirmManually(USER_ID, PARTICIPATION_ID, true);

        verify(participation).confirmManually(NOW);
        assertEquals(Participation.STATUS_CONFIRMED, response.status());
        assertEquals(Participation.CONFIRM_TYPE_MANUAL, response.confirmType());
    }

    @Test
    void confirmManuallyRequiresNoticeAgreement() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> purchaseConfirmService.confirmManually(USER_ID, PARTICIPATION_ID, false));

        assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode());
        verify(participationRepository, never()).findWithLockByParticipationId(any());
    }

    @Test
    void confirmManuallyDeniedForOtherUser() {
        Participation participation = mock(Participation.class);
        when(participation.getUserId()).thenReturn(2L);
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> purchaseConfirmService.confirmManually(USER_ID, PARTICIPATION_ID, true));

        assertEquals(ErrorCode.RESOURCE_ACCESS_DENIED, exception.getErrorCode());
        verify(participation, never()).confirmManually(any());
    }

    @Test
    void findAutoConfirmCandidateIdsUsesPolicyThreshold() {
        when(policyService.getInt(PolicyKey.AUTO_CONFIRM_DAYS)).thenReturn(7);
        Participation participation = mock(Participation.class);
        when(participation.getParticipationId()).thenReturn(PARTICIPATION_ID);
        when(participationRepository.findByStatusAndConfirmHoldFalseAndDeliveredAtBefore(
                Participation.STATUS_DELIVERED, NOW.minusDays(7)))
                .thenReturn(List.of(participation));

        List<Long> candidateIds = purchaseConfirmService.findAutoConfirmCandidateIds();

        assertEquals(List.of(PARTICIPATION_ID), candidateIds);
    }

    @Test
    void autoConfirmIfDueConfirmsOverdueDelivery() {
        when(policyService.getInt(PolicyKey.AUTO_CONFIRM_DAYS)).thenReturn(7);
        Participation participation = mock(Participation.class);
        when(participation.getStatus()).thenReturn(Participation.STATUS_DELIVERED);
        when(participation.isConfirmHold()).thenReturn(false);
        when(participation.getDeliveredAt()).thenReturn(NOW.minusDays(8));
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        purchaseConfirmService.autoConfirmIfDue(PARTICIPATION_ID);

        verify(participation).confirmAutomatically(NOW);
    }

    @Test
    void autoConfirmIfDueSkipsWhenAlreadyConfirmed() {
        Participation participation = mock(Participation.class);
        when(participation.getStatus()).thenReturn(Participation.STATUS_CONFIRMED);
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        purchaseConfirmService.autoConfirmIfDue(PARTICIPATION_ID);

        verify(participation, never()).confirmAutomatically(any());
    }

    @Test
    void autoConfirmIfDueSkipsWhenReturnHoldActive() {
        Participation participation = mock(Participation.class);
        when(participation.getStatus()).thenReturn(Participation.STATUS_DELIVERED);
        when(participation.isConfirmHold()).thenReturn(true);
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        purchaseConfirmService.autoConfirmIfDue(PARTICIPATION_ID);

        verify(participation, never()).confirmAutomatically(any());
    }

    @Test
    void autoConfirmIfDueSkipsWhenNotYetOverdue() {
        when(policyService.getInt(PolicyKey.AUTO_CONFIRM_DAYS)).thenReturn(7);
        Participation participation = mock(Participation.class);
        when(participation.getStatus()).thenReturn(Participation.STATUS_DELIVERED);
        when(participation.isConfirmHold()).thenReturn(false);
        when(participation.getDeliveredAt()).thenReturn(NOW.minusDays(3));
        when(participationRepository.findWithLockByParticipationId(PARTICIPATION_ID)).thenReturn(Optional.of(participation));

        purchaseConfirmService.autoConfirmIfDue(PARTICIPATION_ID);

        verify(participation, never()).confirmAutomatically(any());
    }
}
